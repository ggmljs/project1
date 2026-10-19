package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.Random;

/**
 * 两个启发函数的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：目标状态返回 0、空格不参与统计、手工算例的精确值、
 * 曼哈顿距离不小于错位将牌数、非法入参被拒绝、目标查找表缓存可复用。</p>
 */
public final class PredictorSelfTest {

    private static int checks = 0;

    public static void main(String[] args) {
        Predictor misplaced = new MisplacedTilesPredictor();
        Predictor manhattan = new ManhattanPredictor();

        testGoalIsZero(misplaced, "MISPLACED");
        testGoalIsZero(manhattan, "MANHATTAN");

        testBlankNotCounted(misplaced, manhattan);
        testOneMoveAway(misplaced, manhattan);
        testKnownInstance(misplaced, manhattan);
        testManhattanNotLessThanMisplaced(misplaced, manhattan);
        testGoalTableCache(manhattan);
        testIllegalArguments(misplaced, "MISPLACED");
        testIllegalArguments(manhattan, "MANHATTAN");

        System.out.println("PredictorSelfTest: all tests passed. checks=" + checks);
    }

    private static void testGoalIsZero(Predictor predictor, String name) {
        PuzzleBoard goal = goal();
        expect(predictor.heuristics(goal, goal) == 0, name + "：目标状态的启发值为 0");
    }

    /**
     * 空格不参与统计：把所有数字都错位一格，两种启发值都不应把空格算进去。
     */
    private static void testBlankNotCounted(Predictor misplaced, Predictor manhattan) {
        // 目标：1 2 3 / 4 5 6 / 7 8 _
        // 当前：_ 1 2 / 3 4 5 / 6 7 8  —— 8 个数字全部错位
        PuzzleBoard state = new PuzzleBoard(3, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8});
        expect(misplaced.heuristics(state, goal()) == 8, "MISPLACED：8 个数字全部错位，空格不计入");
        expect(manhattan.heuristics(state, goal()) == 12, "MANHATTAN：该布局的曼哈顿距离为 12");
    }

    /**
     * 与目标状态相差一步移动，两种启发值都应恰好为 1。
     */
    private static void testOneMoveAway(Predictor misplaced, Predictor manhattan) {
        // 目标：1 2 3 / 4 5 6 / 7 8 _
        // 当前：1 2 3 / 4 5 6 / 7 _ 8  —— 空格上移一步
        PuzzleBoard state = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        expect(misplaced.heuristics(state, goal()) == 1, "MISPLACED：一步可解时值为 1");
        expect(manhattan.heuristics(state, goal()) == 1, "MANHATTAN：一步可解时值为 1");
    }

    /**
     * resources/problems.txt 第一个实例的精确值（手工验算过）：
     * 5 _ 8 / 4 2 1 / 7 3 6，目标 1 2 3 / 4 5 6 / 7 8 _。
     * 错位：5、8、2、1、3、6 共 6 个；曼哈顿距离合计 13。
     */
    private static void testKnownInstance(Predictor misplaced, Predictor manhattan) {
        PuzzleBoard state = new PuzzleBoard(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        expect(misplaced.heuristics(state, goal()) == 6, "MISPLACED：problems.txt 首个实例应为 6");
        expect(manhattan.heuristics(state, goal()) == 13, "MANHATTAN：problems.txt 首个实例应为 13");
    }

    /**
     * 对随机可解布局，曼哈顿距离恒不小于错位将牌数（两者都 ≤ 真实步数）。
     */
    private static void testManhattanNotLessThanMisplaced(Predictor misplaced, Predictor manhattan) {
        Random random = new Random(20240301L);
        PuzzleBoard goalBoard = goal();
        int sample = 300;
        for (int i = 0; i < sample; i++) {
            PuzzleBoard state = shuffled(random, goalBoard);
            int m = misplaced.heuristics(state, goalBoard);
            int man = manhattan.heuristics(state, goalBoard);
            expect(man >= m, "随机布局：曼哈顿距离不应小于错位将牌数");
            expect(m >= 0 && man >= 0, "随机布局：启发值非负");
        }
        // 目标状态本身作为随机样本的边界情况再验证一次
        expect(manhattan.heuristics(goalBoard, goalBoard) == 0, "随机布局：目标状态曼哈顿距离为 0");
    }

    /**
     * 目标查找表缓存：换一个“内容相同但不是同一对象”的目标，结果必须仍然正确。
     */
    private static void testGoalTableCache(Predictor manhattan) {
        PuzzleBoard state = new PuzzleBoard(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        int first = manhattan.heuristics(state, goal());
        int second = manhattan.heuristics(state, goal());
        int third = manhattan.heuristics(state, new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0}));
        expect(first == 13 && second == 13 && third == 13,
                "MANHATTAN：目标缓存命中与重建都给出正确结果");
    }

    private static void testIllegalArguments(Predictor predictor, String name) {
        final State notBoard = new State() {
            @Override
            public void draw() {
            }

            @Override
            public State next(core.problem.Action action) {
                return this;
            }

            @Override
            public Iterable<? extends core.problem.Action> actions() {
                return java.util.List.of();
            }
        };

        expectThrows(() -> predictor.heuristics(notBoard, goal()), name + "：非 PuzzleBoard 状态应被拒绝");
        expectThrows(() -> predictor.heuristics(goal(), notBoard), name + "：非 PuzzleBoard 目标应被拒绝");
        expectThrows(() -> predictor.heuristics(null, goal()), name + "：null 状态应被拒绝");
        expectThrows(() -> predictor.heuristics(
                        new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0}),
                        new PuzzleBoard(4, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0})),
                name + "：边长不一致应被拒绝");
    }

    private static PuzzleBoard goal() {
        return new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
    }

    /**
     * Fisher-Yates 洗牌生成随机布局。8 数码一半的排列不可解，
     * 这里只用于比较两个启发函数的相对大小，不要求可解。
     */
    private static PuzzleBoard shuffled(Random random, PuzzleBoard goalBoard) {
        int[] tiles = goalBoard.getTiles();
        for (int i = tiles.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = tiles[i];
            tiles[i] = tiles[j];
            tiles[j] = tmp;
        }
        return new PuzzleBoard(goalBoard.getSize(), tiles);
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("检查失败（预期 IllegalArgumentException）：" + message);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }
}
