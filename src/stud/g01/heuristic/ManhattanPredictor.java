package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * 曼哈顿距离启发函数（成员三负责）。
 *
 * <p>对每个将牌，计算它当前格与目标格的“行差绝对值 + 列差绝对值”，
 * 再对所有将牌求和；空格（数字 0）不参与统计。
 * 每次移动只能让一个将牌沿行或列移动一格，所以该函数同样可采纳，
 * 并且比错位将牌数更紧（对任意状态都有曼哈顿距离 ≥ 错位将牌数）。</p>
 *
 * <p>性能要点：同一轮搜索里 goal 始终是同一个对象，
 * 因此这里把“数字 → 目标行列”的查找表缓存下来，
 * 只在 goal 换了一个对象（或棋盘边长变化）时重建，
 * 避免了对每个子结点重复扫描目标棋盘。</p>
 */
public final class ManhattanPredictor implements Predictor {

    /** 缓存查找表时对应的目标棋盘（按引用比较，一次搜索里恒为同一个对象） */
    private PuzzleBoard cachedGoal;

    /** goalRow[tile]：将牌 tile 在目标棋盘中的行号 */
    private int[] goalRow;

    /** goalCol[tile]：将牌 tile 在目标棋盘中的列号 */
    private int[] goalCol;

    /**
     * 估计 state 到 goal 的曼哈顿距离。
     *
     * @param state 被评估的状态
     * @param goal  目标状态
     * @return 所有将牌到目标位置的曼哈顿距离之和，目标状态本身返回 0
     */
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard board = asBoard(state, "state");
        PuzzleBoard goalBoard = asBoard(goal, "goal");

        if (board.getSize() != goalBoard.getSize()) {
            throw new IllegalArgumentException(
                    "state and goal must have the same size: "
                            + board.getSize() + " vs " + goalBoard.getSize()
            );
        }

        ensureGoalTable(goalBoard);

        int size = board.getSize();
        int cellCount = size * size;
        int distance = 0;
        for (int index = 0; index < cellCount; index++) {
            int tile = board.getTile(index);
            if (tile == 0) {
                // 空格不参与统计
                continue;
            }
            int row = index / size;
            int col = index % size;
            distance += Math.abs(row - goalRow[tile]) + Math.abs(col - goalCol[tile]);
        }
        return distance;
    }

    /**
     * 目标棋盘没变时直接复用已建好的查找表。
     */
    private void ensureGoalTable(PuzzleBoard goalBoard) {
        if (goalBoard == cachedGoal && goalRow != null && goalCol != null
                && goalRow.length == goalBoard.getSize() * goalBoard.getSize()) {
            return;
        }

        int size = goalBoard.getSize();
        int cellCount = size * size;
        int[] rows = new int[cellCount];
        int[] cols = new int[cellCount];

        for (int index = 0; index < cellCount; index++) {
            int tile = goalBoard.getTile(index);
            rows[tile] = index / size;
            cols[tile] = index % size;
        }

        this.cachedGoal = goalBoard;
        this.goalRow = rows;
        this.goalCol = cols;
    }

    private static PuzzleBoard asBoard(State state, String role) {
        if (!(state instanceof PuzzleBoard)) {
            throw new IllegalArgumentException(role + " must be a PuzzleBoard, actual: "
                    + (state == null ? "null" : state.getClass().getName()));
        }
        return (PuzzleBoard) state;
    }

    @Override
    public String toString() {
        return "MANHATTAN";
    }
}
