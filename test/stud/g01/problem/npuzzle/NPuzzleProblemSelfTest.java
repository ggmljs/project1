package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.solver.queue.Node;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 不依赖第三方测试框架的 NPuzzleProblem 基础测试（成员二）。
 *
 * <p>直接运行 main() 即可，预期输出：
 * NPuzzleProblemSelfTest: all tests passed.</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>solvable()：3 阶/4 阶的可解与不可解实例；</li>
 *   <li>solvable()：与广度优先搜索的可达性结果交叉验证（3 阶全可达域）；</li>
 *   <li>applicable()：合法/越界动作、错误类型；与 PuzzleBoard.canMove 的一致性；</li>
 *   <li>stepCost()：每次合法移动代价为 1；</li>
 *   <li>goal()：目标判断；</li>
 *   <li>showSolution()：给定解路径能够正常输出。</li>
 * </ul>
 */
public final class NPuzzleProblemSelfTest {

    private NPuzzleProblemSelfTest() {
    }

    public static void main(String[] args) {
        testSolvableBasic();
        testSolvableCrossCheckWithBfs();
        testApplicable();
        testApplicableMatchesCanMove();
        testStepCost();
        testGoal();
        testShowSolution();
        System.out.println("NPuzzleProblemSelfTest: all tests passed.");
    }

    // -------------------- solvable --------------------

    private static void testSolvableBasic() {
        // 目标状态本身一定可解
        PuzzleBoard goal = standardGoal(3);
        check(problem(goal, goal).solvable(), "the goal state itself must be solvable");

        // 3 阶：交换目标状态的两个相邻数字（逆序数为 1），无解
        PuzzleBoard swapped = new PuzzleBoard(3, new int[]{
                2, 1, 3,
                4, 5, 6,
                7, 8, 0
        });
        check(!problem(swapped, goal).solvable(),
                "3x3 board with one inversion must be unsolvable");

        // 3 阶：resources/problems.txt 第一行实例，可解
        PuzzleBoard fromFile3 = new PuzzleBoard(3, new int[]{
                5, 0, 8,
                4, 2, 1,
                7, 3, 6
        });
        check(problem(fromFile3, goal).solvable(),
                "3x3 instance from problems.txt must be solvable");

        // 3 阶：resources/problems.txt 第二、三行实例，可解
        PuzzleBoard second = new PuzzleBoard(3, new int[]{
                6, 4, 7,
                8, 5, 0,
                3, 2, 1
        });
        check(problem(second, goal).solvable(),
                "3x3 second instance from problems.txt must be solvable");
        PuzzleBoard third = new PuzzleBoard(3, new int[]{
                8, 6, 7,
                2, 5, 4,
                3, 0, 1
        });
        check(problem(third, goal).solvable(),
                "3x3 third instance from problems.txt must be solvable");

        // 4 阶：resources/problems.txt 最后一行实例，可解
        PuzzleBoard goal4 = standardGoal(4);
        PuzzleBoard fromFile4 = new PuzzleBoard(4, new int[]{
                8, 13, 0, 6,
                1, 15, 9, 14,
                3, 4, 5, 11,
                7, 2, 10, 12
        });
        check(problem(fromFile4, goal4).solvable(),
                "4x4 instance from problems.txt must be solvable");

        // 4 阶：经典的 14-15 互换实例（Sam Loyd），无解
        PuzzleBoard loyd = new PuzzleBoard(4, new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 15, 14, 0
        });
        check(!problem(loyd, goal4).solvable(),
                "the classic 15-14 swapped instance must be unsolvable");
    }

    /**
     * 交叉验证：从目标状态做广度优先搜索，枚举 3 阶棋盘的全部可达状态，
     * 然后随机取一批排列，要求 solvable() 的判断与真实可达性完全一致。
     */
    private static void testSolvableCrossCheckWithBfs() {
        int size = 3;
        PuzzleBoard goal = standardGoal(size);

        // BFS 枚举目标状态的所有可达状态
        Set<PuzzleBoard> reachable = new HashSet<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        reachable.add(goal);
        queue.add(goal);
        while (!queue.isEmpty()) {
            PuzzleBoard current = queue.poll();
            for (Action action : current.actions()) {
                PuzzleMove move = (PuzzleMove) action;
                if (current.canMove(move)) {
                    PuzzleBoard next = current.next(move);
                    if (reachable.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        // 3 阶棋盘的全部可达状态数为 9!/2 = 181440
        check(reachable.size() == 181440,
                "BFS should reach 181440 states on the 3x3 board, got " + reachable.size());

        // 固定随机种子，保证测试可以复现
        Random random = new Random(42);
        int[] tiles = new int[size * size];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = i;
        }

        final int samples = 500;
        for (int i = 0; i < samples; i++) {
            shuffle(tiles, random);
            PuzzleBoard board = new PuzzleBoard(size, tiles.clone());
            boolean expected = reachable.contains(board);
            boolean actual = problem(board, goal).solvable();
            check(expected == actual,
                    "solvable() disagrees with BFS for board:\n" + board);
        }
    }

    // -------------------- applicable --------------------

    private static void testApplicable() {
        NPuzzleProblem problem = problem(boardWithCenterBlank(), standardGoal(3));
        PuzzleBoard board = boardWithCenterBlank();

        // 空格在中心，四个方向都可用
        check(problem.applicable(board, PuzzleMove.UP), "UP should be applicable for a center blank");
        check(problem.applicable(board, PuzzleMove.DOWN), "DOWN should be applicable for a center blank");
        check(problem.applicable(board, PuzzleMove.LEFT), "LEFT should be applicable for a center blank");
        check(problem.applicable(board, PuzzleMove.RIGHT), "RIGHT should be applicable for a center blank");

        // 空格在左上角，UP 和 LEFT 越界，不可用
        PuzzleBoard topLeft = new PuzzleBoard(3, new int[]{
                0, 1, 2,
                3, 4, 5,
                6, 7, 8
        });
        check(!problem.applicable(topLeft, PuzzleMove.UP), "UP must not be applicable at the top edge");
        check(!problem.applicable(topLeft, PuzzleMove.LEFT), "LEFT must not be applicable at the left edge");
        check(problem.applicable(topLeft, PuzzleMove.DOWN), "DOWN should be applicable at the top edge");
        check(problem.applicable(topLeft, PuzzleMove.RIGHT), "RIGHT should be applicable at the left edge");

        // 错误的动作类型必须返回 false，而不是抛异常
        check(!problem.applicable(board, null), "a null action must not be applicable");
        check(!problem.applicable(board, new FakeAction()), "a non-PuzzleMove action must not be applicable");

        // 错误的状态类型必须返回 false
        check(!problem.applicable(new FakeState(), PuzzleMove.UP),
                "a non-PuzzleBoard state must reject any action");
    }

    /**
     * 对随机棋盘逐一检查：applicable() 的判断必须与 PuzzleBoard.canMove() 完全一致。
     */
    private static void testApplicableMatchesCanMove() {
        Random random = new Random(7);
        int[] tiles = new int[9];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = i;
        }
        PuzzleBoard goal = standardGoal(3);
        NPuzzleProblem problem = problem(standardGoal(3), goal);

        final int samples = 200;
        for (int i = 0; i < samples; i++) {
            shuffle(tiles, random);
            PuzzleBoard board = new PuzzleBoard(3, tiles.clone());
            for (PuzzleMove move : new PuzzleMove[]{
                    PuzzleMove.UP, PuzzleMove.DOWN, PuzzleMove.LEFT, PuzzleMove.RIGHT}) {
                check(problem.applicable(board, move) == board.canMove(move),
                        "applicable() must agree with canMove() for move " + move);
            }
        }
    }

    // -------------------- stepCost --------------------

    private static void testStepCost() {
        NPuzzleProblem problem = problem(boardWithCenterBlank(), standardGoal(3));
        PuzzleBoard moved = boardWithCenterBlank().next(PuzzleMove.UP);

        for (PuzzleMove move : new PuzzleMove[]{
                PuzzleMove.UP, PuzzleMove.DOWN, PuzzleMove.LEFT, PuzzleMove.RIGHT}) {
            check(problem.stepCost(moved, move) == 1,
                    "every legal move must cost 1");
        }
    }

    // -------------------- goal --------------------

    private static void testGoal() {
        PuzzleBoard goal = standardGoal(3);
        NPuzzleProblem problem = problem(boardWithCenterBlank(), goal);

        check(problem.goal(goal), "goal() must accept the goal state itself");
        check(!problem.goal(boardWithCenterBlank()),
                "goal() must reject a non-goal state");
    }

    // -------------------- showSolution --------------------

    /**
     * 手工构造一条一步的解路径（根结点不计入路径，与框架的
     * generatePath() 行为一致），验证 showSolution() 能正常输出。
     */
    private static void testShowSolution() {
        PuzzleBoard goal = standardGoal(3);
        PuzzleBoard start = new PuzzleBoard(3, new int[]{
                1, 2, 3,
                4, 5, 6,
                7, 0, 8
        });
        NPuzzleProblem problem = problem(start, goal);

        // start --RIGHT--> goal
        PuzzleBoard next = start.next(PuzzleMove.RIGHT);
        check(next.equals(goal), "the manually built path must lead to the goal");

        Node root = new Node(start, null, null, 0);
        Node step1 = new Node(next, root, PuzzleMove.RIGHT, 1);

        Deque<Node> path = new ArrayDeque<>();
        path.addLast(step1);

        // 只要求不抛异常地完整输出
        problem.showSolution(path);
    }

    // -------------------- 工具方法 --------------------

    private static NPuzzleProblem problem(PuzzleBoard initial, PuzzleBoard goal) {
        return new NPuzzleProblem(initial, goal, initial.getSize());
    }

    private static PuzzleBoard standardGoal(int size) {
        int[] tiles = new int[size * size];
        for (int i = 0; i < tiles.length - 1; i++) {
            tiles[i] = i + 1;
        }
        tiles[tiles.length - 1] = 0;
        return new PuzzleBoard(size, tiles);
    }

    private static PuzzleBoard boardWithCenterBlank() {
        return new PuzzleBoard(3, new int[]{
                1, 2, 3,
                4, 0, 5,
                6, 7, 8
        });
    }

    private static void shuffle(int[] tiles, Random random) {
        for (int i = tiles.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = tiles[i];
            tiles[i] = tiles[j];
            tiles[j] = tmp;
        }
    }

    /** 仅用于类型检查的假动作。 */
    private static final class FakeAction extends Action {
        @Override
        public void draw() {
        }

        @Override
        public int stepCost() {
            return 0;
        }
    }

    /** 仅用于类型检查的假状态。 */
    private static final class FakeState extends core.problem.State {
        @Override
        public void draw() {
        }

        @Override
        public core.problem.State next(Action action) {
            return null;
        }

        @Override
        public Iterable<? extends Action> actions() {
            return null;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
