package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * N-Puzzle 问题模型（成员二负责）。
 *
 * <p>本类基于成员一实现的 PuzzleBoard / PuzzleMove，完成：
 * 可解性判断 solvable()、动作可用性判断 applicable()、
 * 单步耗散 stepCost() 和解路径显示 showSolution()。</p>
 *
 * <p>方向约定与成员一保持一致：UP/DOWN/LEFT/RIGHT 表示空格（数字 0）的移动方向。</p>
 */
public class NPuzzleProblem extends Problem {

    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    /**
     * 判断当前问题实例是否有解。
     *
     * <p>判定依据是逆序数（inversions）与空格行号的奇偶关系。
     * 记 n 为棋盘边长，把每个数字替换成它在目标棋盘中的排列名次
     * （空格 0 不参与统计），统计该序列的逆序数 inv：</p>
     *
     * <ul>
     *   <li>n 为奇数：空格左右移动不改变逆序数的奇偶性，上下移动一次
     *       恰好改变偶数对，因此 inv 的奇偶性在整个搜索过程中保持不变；
     *       当且仅当 inv 为偶数时有解。</li>
     *   <li>n 为偶数：上下移动会改变 inv 的奇偶性，但同时空格所在行
     *       （从底部起算，第 1 行为最底行）也改变 1，因此
     *       inv + 空格行号 的奇偶性保持不变；当且仅当初始状态与目标状态的
     *       该奇偶值相同时有解。</li>
     * </ul>
     *
     * <p>目标状态本身 inv 为 0，因此对常见的目标布局
     * （1..n*n-1 顺序排列、空格在右下角），上述规则退化为教科书结论：
     * 奇数阶要求逆序数为偶数；偶数阶要求“逆序数 + 从底数起的空格行号”为奇数。</p>
     */
    @Override
    public boolean solvable() {
        if (!(initialState instanceof PuzzleBoard) || !(goal instanceof PuzzleBoard)) {
            return false;
        }

        PuzzleBoard initial = (PuzzleBoard) initialState;
        PuzzleBoard target = (PuzzleBoard) goal;

        // 两个棋盘的边长必须一致，边长不一致视为无解
        if (initial.getSize() != target.getSize()) {
            return false;
        }

        int width = initial.getSize();
        int initialInversions = inversions(initial, target);
        int goalInversions = inversions(target, target);

        if (width % 2 == 1) {
            // 奇数阶：逆序数奇偶性不变，逆序数为偶数即可解
            return initialInversions % 2 == goalInversions % 2;
        }

        // 偶数阶：逆序数 + 空格行号（从底部数起）的奇偶性不变
        // 空格在第 r 行（0 起），从底数起是第 width - r 行
        int initialParity = initialInversions + (width - initial.getBlankRow());
        int goalParity = goalInversions + (width - target.getBlankRow());
        return (initialParity - goalParity) % 2 == 0;
    }

    /**
     * 计算棋盘 board 相对于目标布局 target 的逆序数。
     *
     * <p>把 board 按行展开，跳过空格 0，得到一个数字序列；
     * 每个数字先替换成它在目标布局中的名次，再统计序列中
     * 前大后小的对数。当 board 与 target 布局相同时结果为 0。</p>
     */
    private int inversions(PuzzleBoard board, PuzzleBoard target) {
        int cellCount = board.getSize() * board.getSize();

        // rank[v]：数字 v 在目标布局（去掉空格）中的名次，从 0 开始
        int[] rank = new int[cellCount];
        int order = 0;
        for (int i = 0; i < cellCount; i++) {
            int tile = target.getTile(i);
            if (tile != 0) {
                rank[tile] = order++;
            }
        }

        // 待统计序列：board 去掉空格后的目标名次序列
        int[] sequence = new int[cellCount - 1];
        int length = 0;
        for (int i = 0; i < cellCount; i++) {
            int tile = board.getTile(i);
            if (tile != 0) {
                sequence[length++] = rank[tile];
            }
        }

        // 暴力统计逆序数。棋盘最多 255 个格（长度 <= 254），
        // O(n^2) 约 3 万次比较，足以满足性能要求
        int inversions = 0;
        for (int i = 0; i < length; i++) {
            for (int j = i + 1; j < length; j++) {
                if (sequence[i] > sequence[j]) {
                    inversions++;
                }
            }
        }
        return inversions;
    }

    /**
     * 单步耗散。N-Puzzle 中每次合法移动代价均为 1，直接取动作自带的代价。
     */
    @Override
    public int stepCost(State state, Action action) {
        if (!(action instanceof PuzzleMove)) {
            throw new IllegalArgumentException("action must be a PuzzleMove");
        }
        return action.stepCost();
    }

    /**
     * 判断动作是否适用于当前状态。
     *
     * <p>按照成员一交接文档约定的流程：状态必须是 PuzzleBoard，
     * 动作必须是 PuzzleMove，具体边界判断委托给 PuzzleBoard.canMove()，
     * 本类不重复实现另一套边界逻辑。</p>
     */
    @Override
    public boolean applicable(State state, Action action) {
        if (!(state instanceof PuzzleBoard) || !(action instanceof PuzzleMove)) {
            return false;
        }

        PuzzleBoard board = (PuzzleBoard) state;
        PuzzleMove move = (PuzzleMove) action;
        return board.canMove(move);
    }

    /**
     * 解路径的可视化。
     *
     * <p>搜索引擎返回的 path 不包含根结点，按第一步到目标步的顺序排列。
     * 输出初始状态后，逐步打印每一步采用的移动方向和移动后的棋盘，
     * 同时保存了动作序列与状态序列，后续可视化程序可以直接读取。</p>
     */
    @Override
    public void showSolution(Deque<Node> path) {
        System.out.println("初始状态:");
        initialState.draw();

        int step = 0;
        for (Node node : path) {
            step++;
            System.out.println("第 " + step + " 步: " + node.getAction());
            node.getState().draw();
        }

        System.out.println("共 " + step + " 步到达目标状态。");
    }
}
