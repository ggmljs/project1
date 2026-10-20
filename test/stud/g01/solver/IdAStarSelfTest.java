package stud.g01.solver;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.heuristic.ManhattanPredictor;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.runner.PuzzleFeeder;
import stud.g01.solver.IdAStar.Options;
import stud.queue.StackFrontier;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * IDA* 的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：已到达目标返回空路径、一步可解、resources/problems.txt 首个 8 数码实例
 * （最优解 21 步）、随机 8 数码实例与 A* 的最优长度一致、无解实例被拦截、
 * 关闭转置表后仍然得到最优解、时间上限、非法入参。</p>
 */
public final class IdAStarSelfTest {

    /** 测试用的小转置表，够用且启动快 */
    private static final int TEST_TABLE_BITS = 18;

    private static int checks = 0;

    public static void main(String[] args) {
        testSolvedInstance();
        testOneMoveInstance();
        testKnownEightPuzzle();
        testRandomEightPuzzleAgainstAStar();
        testUnsolvableInstance();
        testWithoutTranspositionTable();
        testFrameworkWiring();
        testTimeLimit();
        testIllegalArguments();

        System.out.println("IdAStarSelfTest: all tests passed. checks=" + checks);
    }

    private static void testSolvedInstance() {
        Problem problem = problem(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
        IdAStar ida = ida(new Options(true, TEST_TABLE_BITS, 0));

        Deque<Node> path = ida.search(problem);
        expect(path != null, "初始状态即目标：不是无解");
        expect(path.isEmpty(), "初始状态即目标：解路径为空");
        expect(ida.iterations() == 1, "初始状态即目标：一轮迭代即可返回");
        expect(ida.nodesGenerated() == 0, "初始状态即目标：不生成子结点");
    }

    private static void testOneMoveInstance() {
        // 1 2 3 / 4 5 6 / 7 _ 8，空格右移一步到目标
        Problem problem = problem(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        IdAStar ida = ida(new Options(true, TEST_TABLE_BITS, 0));

        Deque<Node> path = ida.search(problem);
        expect(path != null && path.size() == 1, "一步可解：解长为 1");
        expect(path.peekFirst().getAction().equals(PuzzleMove.RIGHT), "一步可解：动作为 RIGHT");
        expect(verify(problem, path), "一步可解：回放动作到达目标");
    }

    private static void testKnownEightPuzzle() {
        // resources/problems.txt 第一个实例，曼哈顿距离 13，最优解 21 步
        Problem problem = problem(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        IdAStar ida = ida(new Options(true, TEST_TABLE_BITS, 0));

        Deque<Node> path = ida.search(problem);
        expect(path != null, "给定 8 数码实例 #1：有解");
        expect(path.size() == 21, "给定 8 数码实例 #1：最优解为 21 步，实际 " + path.size());
        expect(verify(problem, path), "给定 8 数码实例 #1：回放动作到达目标");
        expect(ida.iterations() >= 2, "阈值迭代至少进行两轮");
        expect(ida.thresholdLimit() == 21, "最后一轮阈值等于最优解长度");
        expect(ida.nodesGenerated() > 0 && ida.nodesExpanded() > 0, "统计生成了结点");
    }

    /**
     * 随机 8 数码实例：IDA* 与 A* 都返回最优解，长度必须一致。
     */
    private static void testRandomEightPuzzleAgainstAStar() {
        PredicateHarness harness = new PredicateHarness();
        PuzzleFeeder feeder = new PuzzleFeeder();

        for (Problem problem : harness.randomProblems(3, 4, 20240403L)) {
            int heuristic0 = problem.root(new ManhattanPredictor()).getHeuristic();

            AbstractSearcher astar = feeder.getAStar(HeuristicType.MANHATTAN);
            Deque<Node> expected = astar.search(problem);

            IdAStar ida = ida(new Options(true, TEST_TABLE_BITS, 0));
            Deque<Node> actual = ida.search(problem);

            expect(expected != null && actual != null, "随机 8 数码（h0=" + heuristic0 + "）：两者都有解");
            expect(actual.size() == expected.size(),
                    "随机 8 数码（h0=" + heuristic0 + "）：IDA* 与 A* 的解长一致，"
                            + actual.size() + " vs " + expected.size());
            expect(verify(problem, actual), "随机 8 数码（h0=" + heuristic0 + "）：IDA* 解路径可回放到目标");
        }
    }

    private static void testUnsolvableInstance() {
        // 经典的 14-15 互换无解实例
        int[] tiles = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 14, 0};
        Problem problem = problem(4, tiles);
        expect(!problem.solvable(), "14-15 互换的 15 数码实例无解");

        IdAStar ida = ida(new Options(true, TEST_TABLE_BITS, 0));
        Deque<Node> path = ida.search(problem);
        expect(path == null, "无解实例返回 null");
        expect(!ida.timedOut(), "无解实例不能被误判为超时");
        expect(ida.iterations() == 0, "无解实例在搜索前就被拦截");
    }

    private static void testWithoutTranspositionTable() {
        Problem problem = problem(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        IdAStar ida = ida(Options.withoutTranspositionTable());

        Deque<Node> path = ida.search(problem);
        expect(path != null && path.size() == 21, "关闭转置表后仍得到最优解 21 步");
        expect(verify(problem, path), "关闭转置表后解路径可回放到目标");
        expect(ida.table() == null, "关闭转置表时 table() 为 null");
        expect(ida.transpositionHits() == 0, "关闭转置表时没有转置表剪枝");
        expect(ida.cyclePrunes() > 0, "关闭转置表时路径去环仍在工作");
    }

    /**
     * 框架接线：EngineFeeder.getIdaStar() 返回的必须是本类（经 stud.solver.IdAStar 适配），
     * 并且能直接求出 resources/problems.txt 首个实例的 21 步最优解。
     */
    private static void testFrameworkWiring() {
        Problem problem = problem(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        PuzzleFeeder feeder = new PuzzleFeeder();

        AbstractSearcher searcher = feeder.getIdaStar(HeuristicType.MANHATTAN);
        expect(searcher instanceof IdAStar, "EngineFeeder.getIdaStar 返回 IDA* 实例");

        Deque<Node> path = searcher.search(problem);
        expect(path != null && path.size() == 21, "框架接出的 IDA* 求出 21 步最优解");
        expect(verify(problem, path), "框架接出的 IDA* 解路径可回放到目标");
    }

    private static void testTimeLimit() {        // 15 数码“最坏情况”实例（80 步），20 ms 内不可能解完，用来验证超时分支
        int[] tiles = new int[]{
                15, 14, 13, 12,
                11, 10, 9, 8,
                7, 6, 5, 4,
                3, 1, 2, 0
        };
        Problem problem = problem(4, tiles);
        IdAStar ida = ida(new Options(true, 16, 20));

        Deque<Node> path = ida.search(problem);
        expect(path == null, "触发时间上限时返回 null");
        expect(ida.timedOut(), "timedOut() 标记超时，以便与“无解”区分");
    }

    private static void testIllegalArguments() {
        expectThrows(() -> ida(new Options(true, TEST_TABLE_BITS, 0)).search(null), "problem 为 null 应报错");
        expectThrows(() -> new IdAStar(new StackFrontier(), null), "predictor 为 null 应报错");
        expectThrows(() -> new IdAStar(new StackFrontier(), new ManhattanPredictor(), null),
                "options 为 null 应报错");
        expectThrows(() -> new Options(true, 7, 0), "tableBits 过小应报错");
        expectThrows(() -> new Options(true, 27, 0), "tableBits 过大应报错");
        expectThrows(() -> new Options(true, TEST_TABLE_BITS, -1), "时间上限为负应报错");

        // 非 N-Puzzle 状态应给出明确异常
        Problem notPuzzle = new Problem(new ForeignState(), new ForeignState()) {
            @Override
            public boolean solvable() {
                return true;
            }

            @Override
            public int stepCost(State state, Action action) {
                return 1;
            }

            @Override
            public boolean applicable(State state, Action action) {
                return false;
            }

            @Override
            public void showSolution(Deque<Node> path) {
            }
        };
        expectThrows(() -> ida(new Options(true, TEST_TABLE_BITS, 0)).search(notPuzzle),
                "非 N-Puzzle 状态应报错");
    }

    private static IdAStar ida(Options options) {
        return new IdAStar(new StackFrontier(), new ManhattanPredictor(), options);
    }

    private static Problem problem(int size, int[] initialTiles) {
        int[] goalTiles = new int[size * size];
        for (int i = 0; i < goalTiles.length - 1; i++) {
            goalTiles[i] = i + 1;
        }
        return new NPuzzleProblem(
                new PuzzleBoard(size, initialTiles),
                new PuzzleBoard(size, goalTiles),
                size);
    }

    private static boolean verify(Problem problem, Deque<Node> path) {
        PuzzleBoard state = (PuzzleBoard) problem.root().getState();
        for (Node node : path) {
            if (node.getAction() == null) {
                return false;
            }
            state = state.next(node.getAction());
        }
        return problem.goal(state);
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("检查失败（本应抛出异常）：" + message);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }

    /**
     * 生成随机可解实例的小工具，避免测试依赖外部实例文件。
     */
    private static final class PredicateHarness {

        List<Problem> randomProblems(int size, int count, long seed) {
            Random random = new Random(seed);
            PuzzleBoard goal = new PuzzleBoard(size, sortedTiles(size));
            List<Problem> problems = new ArrayList<>();
            while (problems.size() < count) {
                int[] tiles = goal.getTiles();
                for (int i = tiles.length - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    int tmp = tiles[i];
                    tiles[i] = tiles[j];
                    tiles[j] = tmp;
                }
                Problem problem = new NPuzzleProblem(new PuzzleBoard(size, tiles), goal, size);
                if (problem.solvable()) {
                    problems.add(problem);
                }
            }
            return problems;
        }

        private int[] sortedTiles(int size) {
            int[] tiles = new int[size * size];
            for (int i = 0; i < tiles.length - 1; i++) {
                tiles[i] = i + 1;
            }
            return tiles;
        }
    }

    /**
     * 非 N-Puzzle 的状态，仅用于验证参数类型检查。
     */
    private static final class ForeignState extends State {
        @Override
        public void draw() {
        }

        @Override
        public State next(Action action) {
            return this;
        }

        @Override
        public Iterable<? extends Action> actions() {
            return List.of();
        }
    }
}
