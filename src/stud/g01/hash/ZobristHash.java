package stud.g01.hash;

import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Zobrist 哈希（成员四负责）。
 *
 * <p>为“棋子编号 × 格子编号”的每一种组合预先随机出一个 64 位整数，
 * 一个棋盘状态的哈希值就是“棋盘上每个棋子所在格子的随机数”的异或和。
 * 异或的两个重要性质使它可以增量维护：</p>
 *
 * <ul>
 *   <li>同一个随机数异或两次会抵消，所以移动一步时只要把“受影响的 4 个
 *       （棋子, 格子）组合”再异或一次，就能从父状态的哈希值 O(1) 得到子状态的哈希值；</li>
 *   <li>异或满足交换律，所以计算顺序与棋盘遍历顺序无关。</li>
 * </ul>
 *
 * <p>随机表用固定种子生成，因此同一棋盘边长下每次运行、每个 JVM 得到的
 * 哈希值完全一致，实验与自测都可复现。不同边长使用不同的随机表。</p>
 *
 * <p>注意：Zobrist 哈希只是“重复状态”的快速指纹，不能替代
 * {@link PuzzleBoard#equals(Object)}。理论上存在哈希冲突（两个不同棋盘得到
 * 同一个 key），64 位下概率约为 2^-64，IDA* 的转置表按这个前提使用；
 * 仓库里的 {@code ZobristHashSelfTest} 枚举了 8 数码全部可达状态做碰撞检查。</p>
 */
public final class ZobristHash {

    /** 随机表种子前缀；固定值保证可复现 */
    private static final long SEED = 0x9E3779B97F4A7C15L;

    /** 每种棋盘边长只建一张随机表，多个搜索之间共享 */
    private static final Map<Integer, ZobristHash> CACHE = new HashMap<>();

    /** 棋盘边长 */
    private final int size;

    /** 格子数 size * size，同时也是棋子编号的个数 */
    private final int cellCount;

    /** random[tile][cell]：棋子 tile 位于格子 cell 时贡献的随机数 */
    private final long[][] random;

    private ZobristHash(int size) {
        this.size = size;
        this.cellCount = size * size;
        SplittableRandom rng = new SplittableRandom(SEED * size);
        this.random = new long[cellCount][cellCount];
        for (int tile = 0; tile < cellCount; tile++) {
            for (int cell = 0; cell < cellCount; cell++) {
                random[tile][cell] = rng.nextLong();
            }
        }
    }

    /**
     * 取得指定边长的 Zobrist 哈希表（同一进程内缓存复用）。
     *
     * @param size 棋盘边长，至少为 2
     * @return 该边长的哈希表
     * @throws IllegalArgumentException 边长不合法时抛出
     */
    public static ZobristHash forSize(int size) {
        if (size < 2 || size > 10) {
            throw new IllegalArgumentException("不支持的棋盘边长：" + size + "（应为 2..10）");
        }
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(size, ZobristHash::new);
        }
    }

    /**
     * 从头计算一个棋盘的 Zobrist 哈希值。
     *
     * @param board 待计算的棋盘
     * @return 64 位哈希值
     * @throws IllegalArgumentException 棋盘边长与当前哈希表不一致时抛出
     */
    public long keyOf(PuzzleBoard board) {
        requireSameSize(board);
        long key = 0L;
        for (int cell = 0; cell < cellCount; cell++) {
            key ^= random[board.getTile(cell)][cell];
        }
        return key;
    }

    /**
     * 增量计算：已知移动前棋盘的哈希值，求空格移动一步后棋盘的哈希值。
     *
     * <p>一次移动只改变两个格子：空格原来的格子（空格离开、棋子填入）和
     * 目标格子（棋子离开、空格填入），因此只需异或 4 个随机数，
     * 不必重新扫描整个棋盘。</p>
     *
     * @param key    移动前棋盘的 Zobrist 哈希值
     * @param before 移动前的棋盘
     * @param move   空格移动方向
     * @return 移动后棋盘的 Zobrist 哈希值
     * @throws IllegalArgumentException 棋盘边长不一致或该移动不合法时抛出
     */
    public long afterMove(long key, PuzzleBoard before, PuzzleMove move) {
        requireSameSize(before);
        if (move == null) {
            throw new IllegalArgumentException("move cannot be null");
        }
        if (!before.canMove(move)) {
            throw new IllegalArgumentException("illegal move " + move + " for board " + before);
        }

        int from = before.getBlankIndex();
        int to = targetIndex(before.getBlankIndex(), move.getDirection());
        int tile = before.getTile(to);

        // 去掉“空格在 from、棋子 tile 在 to”，换成“棋子 tile 在 from、空格在 to”
        return key
                ^ random[0][from]
                ^ random[tile][from]
                ^ random[tile][to]
                ^ random[0][to];
    }

    /**
     * 空格沿给定方向移动后的目标格子下标。
     */
    private int targetIndex(int blankIndex, PuzzleMove.Direction direction) {
        switch (direction) {
            case UP:
                return blankIndex - size;
            case DOWN:
                return blankIndex + size;
            case LEFT:
                return blankIndex - 1;
            case RIGHT:
                return blankIndex + 1;
            default:
                throw new IllegalArgumentException("不支持的移动方向：" + direction);
        }
    }

    private void requireSameSize(PuzzleBoard board) {
        if (board == null) {
            throw new IllegalArgumentException("board cannot be null");
        }
        if (board.getSize() != size) {
            throw new IllegalArgumentException(
                    "棋盘边长不一致：哈希表为 " + size + "，棋盘为 " + board.getSize());
        }
    }

    /**
     * @return 该哈希表对应的棋盘边长
     */
    public int size() {
        return size;
    }

    /**
     * @return 格子数（也是棋子编号个数）
     */
    public int cellCount() {
        return cellCount;
    }

    @Override
    public String toString() {
        return "ZobristHash(size=" + size + ", cells=" + cellCount + ")";
    }
}
