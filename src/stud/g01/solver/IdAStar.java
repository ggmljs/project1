package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.hash.TranspositionTable;
import stud.g01.hash.ZobristHash;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;

import java.util.Arrays;
import java.util.Deque;

/**
 * IDA*（Iterative Deepening A*，迭代加深 A*）搜索引擎（成员四负责，第二阶段）。
 *
 * <p>与框架的 A*（{@link core.solver.algorithm.searcher.BestFirstSearcher}）
 * 每次都把整棵搜索树放进 Open 表不同，IDA* 只保留一条从根到当前结点的路径，空间是
 * O(解的长度)，代价是同一批状态会在不同阈值的迭代里被重复访问。算法流程：</p>
 *
 * <ol>
 *   <li>取初始状态的 f = g + h 作为阈值 threshold；</li>
 *   <li>以 threshold 为上限做深度优先搜索，f &gt; threshold 的结点立即剪掉，
 *       并记下“被剪掉的最小 f”，作为下一轮的阈值；</li>
 *   <li>搜索到目标就返回路径；一轮结束仍未找到，就把阈值提高到上一步记录的值，
 *       重新从根开始搜；</li>
 *   <li>如果一轮里没有任何结点超过阈值（即整个状态空间都已搜完），说明无解。</li>
 * </ol>
 *
 * <p>在启发函数可采纳（不高估）时，IDA* 与 A* 一样返回最优解；在启发函数满足
 * 一致性时，阈值严格递增，每个解长度对应的迭代至多一次，因此不会漏解。</p>
 *
 * <p>本实现针对第二阶段（15-Puzzle）做了三处工程改进：</p>
 *
 * <ul>
 *   <li><b>路径去环</b>：用当前 DFS 路径上各状态的 Zobrist 哈希做环检测，
 *       空格来回移动形成的环（… → A → B → A → …）在 O(路径长度) 内被剪掉；</li>
 *   <li><b>Zobrist 增量哈希</b>：子状态的哈希值由父状态的哈希值异或 4 个随机数
 *       得到，不必重新扫描棋盘（见 {@link ZobristHash#afterMove}）；</li>
 *   <li><b>转置表</b>：{@link TranspositionTable} 记录“本轮以多大的 g 访问过某状态”，
 *       剪掉经不同路径重复到达的子树，把 IDA* 从纯树搜索变成带记忆的图搜索。</li>
 * </ul>
 *
 * <p>与框架的接线：{@code core.runner.EngineFeeder.getIdaStar()} 固定调用
 * {@code new IdAStar(frontier, predictor)}，所以本类保留该构造函数；
 * 框架导入的是 {@code stud.solver.IdAStar}，那里是一个只有构造函数的适配子类。</p>
 */
public class IdAStar extends AbstractSearcher {

    /**
     * 搜索参数。
     *
     * <p>默认值面向第二阶段 15-Puzzle：启用转置表、2^22 个槽位、不限时。
     * 做消融实验时可以关闭转置表或限制单实例耗时。</p>
     */
    public static final class Options {

        private final boolean transpositionTable;
        private final int tableBits;
        private final long timeLimitMillis;

        /**
         * 默认参数：启用转置表、2^22 槽位、不限时。
         */
        public Options() {
            this(true, 22, 0L);
        }

        /**
         * @param transpositionTable 是否启用转置表
         * @param tableBits          槽位数的二进制位数，见 {@link TranspositionTable}
         * @param timeLimitMillis    单个实例的时间上限（毫秒），0 表示不限时
         */
        public Options(boolean transpositionTable, int tableBits, long timeLimitMillis) {
            if (tableBits < 8 || tableBits > 26) {
                throw new IllegalArgumentException("tableBits 必须在 [8, 26] 之间：" + tableBits);
            }
            if (timeLimitMillis < 0) {
                throw new IllegalArgumentException("timeLimitMillis 不能为负：" + timeLimitMillis);
            }
            this.transpositionTable = transpositionTable;
            this.tableBits = tableBits;
            this.timeLimitMillis = timeLimitMillis;
        }

        /**
         * 关闭转置表，只保留路径去环，用于消融实验。
         */
        public static Options withoutTranspositionTable() {
            return new Options(false, 22, 0L);
        }

        public boolean transpositionTable() {
            return transpositionTable;
        }

        public int tableBits() {
            return tableBits;
        }

        public long timeLimitMillis() {
            return timeLimitMillis;
        }

        /**
         * 返回只修改时间上限的副本。
         */
        public Options withTimeLimitMillis(long millis) {
            return new Options(transpositionTable, tableBits, millis);
        }

        /**
         * 返回只修改表大小的副本。
         */
        public Options withTableBits(int bits) {
            return new Options(transpositionTable, bits, timeLimitMillis);
        }
    }

    /**
     * 内部超时信号。用栈展开的方式退出递归，不参与业务逻辑。
     */
    private static final class Timeout extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Timeout() {
            super(null, null, false, false);
        }
    }

    /** 每展开多少个结点检查一次时间，避免在热循环里频繁读时钟 */
    private static final int TIME_CHECK_MASK = 1023;

    private final Predictor predictor;
    private final Options options;

    /** 当前问题对应的 Zobrist 哈希表（按棋盘边长缓存） */
    private ZobristHash zobrist;

    /** 转置表；关闭转置表时为 null */
    private TranspositionTable table;

    /** 当前正在求解的问题 */
    private Problem problem;

    /** 当前 DFS 路径上各状态的 Zobrist 哈希，用于路径去环 */
    private long[] pathKeys = new long[128];

    /** 路径栈中有效元素个数（根为 1） */
    private int pathSize;

    /** 本轮搜索中“超过阈值的最小 f”，即下一轮的阈值 */
    private int nextThreshold;

    private long deadlineNanos;
    private boolean timedOut;
    private int iterations;
    private int lastThreshold;
    private long transpositionHits;
    private long cyclePrunes;

    /**
     * 框架 {@code EngineFeeder.getIdaStar()} 使用的构造函数。
     *
     * @param frontier  框架传入的栈式 Frontier（IDA* 自身用递归实现深度优先，
     *                  该 Frontier 会在每次搜索前清空；保留它是为了满足框架的接口）
     * @param predictor 启发函数（可采纳即可，第二阶段使用曼哈顿距离）
     */
    public IdAStar(Frontier frontier, Predictor predictor) {
        this(frontier, predictor, new Options());
    }

    /**
     * 带搜索参数的构造函数，供消融实验使用。
     *
     * @param frontier  栈式 Frontier
     * @param predictor 启发函数
     * @param options   搜索参数
     */
    public IdAStar(Frontier frontier, Predictor predictor, Options options) {
        super(frontier);
        if (predictor == null) {
            throw new IllegalArgumentException("predictor cannot be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
        this.predictor = predictor;
        this.options = options;
    }

    /**
     * 搜索一条从初始状态到目标状态的最优路径。
     *
     * <p>返回值与框架的约定一致：不包含根结点，按第一步到目标步排列，
     * {@code path.size()} 就是解的长度。无解、或者触发时间上限（见
     * {@link #timedOut()}）时返回 null。</p>
     *
     * @param problem N-Puzzle 问题实例
     * @return 解路径；无解或超时返回 null
     * @throws IllegalArgumentException 问题或状态类型不支持时抛出
     */
    @Override
    public Deque<Node> search(Problem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("problem cannot be null");
        }
        if (!(problem.root().getState() instanceof PuzzleBoard)) {
            throw new IllegalArgumentException(
                    "IdAStar 目前只支持 N-Puzzle（状态必须是 PuzzleBoard，实际为 "
                            + problem.root().getState().getClass().getName() + "）");
        }

        this.problem = problem;
        frontier.clear();
        explored.clear();
        nodesGenerated = 0;
        nodesExpanded = 0;
        iterations = 0;
        lastThreshold = 0;
        transpositionHits = 0;
        cyclePrunes = 0;
        timedOut = false;
        pathSize = 0;

        deadlineNanos = options.timeLimitMillis() > 0
                ? System.nanoTime() + options.timeLimitMillis() * 1_000_000L
                : Long.MAX_VALUE;

        // 无解实例在搜索前直接拦截，与框架 A* 的行为一致
        if (!problem.solvable()) {
            return null;
        }

        Node root = problem.root(predictor);
        int threshold = root.getHeuristic();
        lastThreshold = threshold;

        PuzzleBoard rootBoard = (PuzzleBoard) root.getState();
        zobrist = ZobristHash.forSize(rootBoard.getSize());
        prepareTable();

        pushKey(zobrist.keyOf(rootBoard));

        try {
            while (true) {
                iterations++;
                nextThreshold = Integer.MAX_VALUE;
                if (table != null) {
                    table.beginIteration();
                }

                Node goal = dfs(root, threshold);

                if (goal != null) {
                    return generatePath(goal);
                }
                if (nextThreshold == Integer.MAX_VALUE) {
                    // 一轮里没有任何结点被阈值剪掉，状态空间已穷尽
                    return null;
                }
                if (nextThreshold <= threshold) {
                    // 启发函数不满足一致性时阈值可能不增，防止死循环
                    throw new IllegalStateException(
                            "启发函数不满足一致性：下一阈值 " + nextThreshold
                                    + " 不大于当前阈值 " + threshold);
                }
                threshold = nextThreshold;
                lastThreshold = threshold;

                // 下一轮重新从根开始，路径栈复位
                pathSize = 0;
                pushKey(zobrist.keyOf(rootBoard));
            }
        } catch (Timeout ignored) {
            timedOut = true;
            return null;
        }
    }

    /**
     * 准备转置表：关闭时置空；开启时复用同尺寸的表并清空，否则新建。
     */
    private void prepareTable() {
        if (!options.transpositionTable()) {
            table = null;
            return;
        }
        if (table == null || table.tableBits() != options.tableBits()) {
            table = new TranspositionTable(options.tableBits());
        } else {
            table.clear();
        }
    }

    /**
     * 以 threshold 为上限的深度优先搜索。
     *
     * @param node      当前结点（其 h 值已由 Predictor 算好）
     * @param threshold 当前阈值
     * @return 找到目标时返回目标结点，否则返回 null
     */
    private Node dfs(Node node, int threshold) {
        int g = node.getPathCost();
        int f = g + node.getHeuristic();

        // f 超过阈值：剪枝，并记录下一轮的最小阈值
        if (f > threshold) {
            if (f < nextThreshold) {
                nextThreshold = f;
            }
            return null;
        }

        if ((nodesExpanded & TIME_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
            throw new Timeout();
        }

        long key = pathKeys[pathSize - 1];

        // 转置表：本轮已经以不大于 g 的代价访问过该状态，子树必已搜完
        if (table != null) {
            if (table.bestCost(key) <= g) {
                transpositionHits++;
                return null;
            }
            table.store(key, g);
        }

        if (problem.goal(node.getState())) {
            return node;
        }

        nodesExpanded++;

        PuzzleBoard board = (PuzzleBoard) node.getState();
        for (Node child : problem.childNodes(node, predictor)) {
            nodesGenerated++;
            PuzzleMove move = (PuzzleMove) child.getAction();

            // 增量哈希 + 路径去环
            long childKey = zobrist.afterMove(key, board, move);
            if (onPath(childKey)) {
                cyclePrunes++;
                continue;
            }

            pushKey(childKey);
            Node found = dfs(child, threshold);
            pathSize--;
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 判断某个状态的哈希是否已经出现在当前 DFS 路径上。
     *
     * <p>路径长度就是解的长度（15-Puzzle 不超过 80 左右），线性扫描的
     * 开销远小于重新分配一个 HashSet；同时它不依赖装箱，缓存友好。</p>
     */
    private boolean onPath(long key) {
        for (int i = 0; i < pathSize; i++) {
            if (pathKeys[i] == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * 路径栈压入一个哈希，必要时扩容。
     */
    private void pushKey(long key) {
        if (pathSize == pathKeys.length) {
            pathKeys = Arrays.copyOf(pathKeys, pathSize * 2);
        }
        pathKeys[pathSize++] = key;
    }

    /**
     * @return 本次搜索使用的启发函数
     */
    public Predictor predictor() {
        return predictor;
    }

    /**
     * @return 本次搜索一共进行了多少轮阈值迭代
     */
    public int iterations() {
        return iterations;
    }

    /**
     * @return 最近一轮使用的阈值（搜索成功时为解的长度，失败时为最后的上限）
     */
    public int thresholdLimit() {
        return lastThreshold;
    }

    /**
     * @return 被转置表剪掉的结点数
     */
    public long transpositionHits() {
        return transpositionHits;
    }

    /**
     * @return 被路径去环剪掉的结点数
     */
    public long cyclePrunes() {
        return cyclePrunes;
    }

    /**
     * @return 是否因为触发时间上限而提前结束（此时 search 返回 null，不能当成无解）
     */
    public boolean timedOut() {
        return timedOut;
    }

    /**
     * @return 本次搜索使用的转置表；未启用时为 null
     */
    public TranspositionTable table() {
        return table;
    }
}
