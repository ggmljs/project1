package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * 错位将牌数（misplaced tiles / 不在位将牌数）启发函数（成员三负责）。
 *
 * <p>直接统计“不在目标位置上的将牌个数”，空格（数字 0）不参与统计。
 * 每一步至多把一个将牌归位，所以该函数是可采纳的（admissible），
 * 但比曼哈顿距离松弛，扩展的结点更多。</p>
 */
public final class MisplacedTilesPredictor implements Predictor {

    /**
     * 估计 state 到 goal 的距离：错位将牌个数。
     *
     * @param state 被评估的状态
     * @param goal  目标状态
     * @return 不在目标位置上的将牌个数，目标状态本身返回 0
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

        int cellCount = board.getSize() * board.getSize();
        int misplaced = 0;
        for (int index = 0; index < cellCount; index++) {
            int tile = board.getTile(index);
            if (tile == 0) {
                // 空格不参与统计
                continue;
            }
            if (tile != goalBoard.getTile(index)) {
                misplaced++;
            }
        }
        return misplaced;
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
        return "MISPLACED";
    }
}
