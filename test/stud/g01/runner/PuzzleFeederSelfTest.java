package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.heuristic.ManhattanPredictor;
import stud.g01.heuristic.MisplacedTilesPredictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static core.solver.queue.EvaluationType.FULL;
import static core.solver.queue.EvaluationType.HEURISTIC;
import static core.solver.queue.EvaluationType.PATH_COST;

/**
 * PuzzleFeeder 的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：实例文本解析、非法输入报错、Frontier/启发函数工厂，
 * 以及“解析 → A* 搜索 → 校验解路径”的端到端流程。</p>
 */
public final class PuzzleFeederSelfTest {

    /** resources/problems.txt 第一个实例，最优解为 21 步 */
    private static final String INSTANCE_1 =
            "3 5 0 8 4 2 1 7 3 6 1 2 3 4 5 6 7 8 0";

    /** 目标布局交换两个数字后的不可解实例（2 1 3 / 4 5 6 / 7 8 _） */
    private static final String UNSOLVABLE =
            "3 2 1 3 4 5 6 7 8 0 1 2 3 4 5 6 7 8 0";

    private static int checks = 0;

    public static void main(String[] args) {
        PuzzleFeeder feeder = new PuzzleFeeder();

        testParseSingleInstance(feeder);
        testSkipBlankAndCommentLines(feeder);
        testParseErrorCount(feeder);
        testParseErrorNotInteger(feeder);
        testParseEmpty(feeder);
        testFrontierFactory(feeder);
        testPredictorFactory(feeder);
        testUnsolvableInstance(feeder);
        testEndToEndAStar(feeder);

        System.out.println("PuzzleFeederSelfTest: all tests passed. checks=" + checks);
    }

    private static void testParseSingleInstance(PuzzleFeeder feeder) {
        ArrayList<Problem> problems = feeder.getProblems(lines(INSTANCE_1));
        expect(problems.size() == 1, "解析：一行文本生成一个问题实例");

        Problem problem = problems.get(0);
        expect(problem.solvable(), "解析：problems.txt 第一个实例可解");

        PuzzleBoard initial = (PuzzleBoard) problem.root().getState();
        expect(initial.getSize() == 3, "解析：棋盘边长为 3");
        expect(initial.getTile(0) == 5 && initial.getBlankIndex() == 1,
                "解析：初始布局与第一行文本一致（首格为 5，空格在下标 1）");
    }

    private static void testSkipBlankAndCommentLines(PuzzleFeeder feeder) {
        ArrayList<Problem> problems = feeder.getProblems(
                lines("", "   ", "# 这是注释", INSTANCE_1, ""));
        expect(problems.size() == 1, "解析：空行与 # 注释被跳过");
    }

    private static void testParseErrorCount(PuzzleFeeder feeder) {
        expectThrows(() -> feeder.getProblems(lines("3 1 2 3")), "解析：字段个数不足应报错");
        // 3 阶需要 1 + 9 + 9 = 19 个字段，这里多给一个
        expectThrows(() -> feeder.getProblems(lines(INSTANCE_1 + " 9")), "解析：字段个数过多应报错");
    }

    private static void testParseErrorNotInteger(PuzzleFeeder feeder) {
        expectThrows(() -> feeder.getProblems(lines("3 5 0 8 x 2 1 7 3 6 1 2 3 4 5 6 7 8 0")),
                "解析：非整数字段应报错");
        expectThrows(() -> feeder.getProblems(lines("1 0 1 0")), "解析：边长小于 2 应报错");
    }

    private static void testParseEmpty(PuzzleFeeder feeder) {
        expectThrows(() -> feeder.getProblems(lines()), "解析：空输入应报错");
        expectThrows(() -> feeder.getProblems(null), "解析：null 输入应报错");
    }

    private static void testFrontierFactory(PuzzleFeeder feeder) {
        Frontier full = feeder.getFrontier(FULL);
        Frontier pathCost = feeder.getFrontier(PATH_COST);
        Frontier heuristic = feeder.getFrontier(HEURISTIC);
        expect(full != null && pathCost != null && heuristic != null, "工厂：三种估值机制都能生成 Frontier");

        PuzzleBoard state = (PuzzleBoard) feeder.getProblems(lines(INSTANCE_1)).get(0).root().getState();
        full.offer(new Node(state, null, null, 3, 4));
        expect(full.size() == 1 && full.contains(new Node(state, null, null, 0, 0)),
                "工厂：Frontier 支持按状态判重");
        expect(!full.isEmpty() && full.poll() != null, "工厂：Frontier 可以正常出队");

        expectThrows(() -> feeder.getFrontier(null), "工厂：null 估值类型应报错");
    }

    private static void testPredictorFactory(PuzzleFeeder feeder) {
        Predictor misplaced = feeder.getPredictor(HeuristicType.MISPLACED);
        Predictor manhattan = feeder.getPredictor(HeuristicType.MANHATTAN);
        expect(misplaced instanceof MisplacedTilesPredictor, "工厂：MISPLACED 返回错位将牌数");
        expect(manhattan instanceof ManhattanPredictor, "工厂：MANHATTAN 返回曼哈顿距离");

        // DISJOINT_PATTERN 属于成员五的第三阶段任务，这里只校验“接入点存在且明确”
        expectThrows(() -> feeder.getPredictor(HeuristicType.DISJOINT_PATTERN),
                "工厂：DISJOINT_PATTERN 尚未实现，应给出明确的未实现异常");
        expectThrows(() -> feeder.getPredictor(HeuristicType.PF_GRID),
                "工厂：非 N-Puzzle 的启发函数类型应报错");
        expectThrows(() -> feeder.getPredictor(null), "工厂：null 启发函数类型应报错");
    }

    private static void testUnsolvableInstance(PuzzleFeeder feeder) {
        Problem problem = feeder.getProblems(lines(UNSOLVABLE)).get(0);
        expect(!problem.solvable(), "可解性：交换两个数字的布局不可解");
        AbstractSearcher searcher = feeder.getAStar(HeuristicType.MANHATTAN);
        expect(searcher.search(problem) == null, "可解性：无解实例直接返回 null，不做无谓搜索");
    }

    private static void testEndToEndAStar(PuzzleFeeder feeder) {
        Problem problem = feeder.getProblems(lines(INSTANCE_1)).get(0);
        int misplacedGenerated = Integer.MAX_VALUE;
        int manhattanGenerated = Integer.MAX_VALUE;

        for (HeuristicType type : List.of(HeuristicType.MISPLACED, HeuristicType.MANHATTAN)) {
            AbstractSearcher searcher = feeder.getAStar(type);
            Deque<Node> path = searcher.search(problem);

            expect(path != null, "端到端：" + type + " 找到解");
            expect(path.size() == 21, "端到端：" + type + " 的最优解长度为 21，实际 " + path.size());
            expect(verifyPath(problem, path), "端到端：" + type + " 的解路径逐步走完确实到达目标状态");
            expect(searcher.nodesGenerated() > 0 && searcher.nodesExpanded() > 0,
                    "端到端：" + type + " 的结点统计不为 0");

            if (type == HeuristicType.MISPLACED) {
                misplacedGenerated = searcher.nodesGenerated();
            } else {
                manhattanGenerated = searcher.nodesGenerated();
            }
        }

        // 阶段一实验结论：曼哈顿距离更紧，生成的结点数明显更少
        expect(manhattanGenerated < misplacedGenerated,
                "端到端：MANHATTAN(" + manhattanGenerated + ") 生成的结点数应少于 MISPLACED("
                        + misplacedGenerated + ")");
    }

    /**
     * 从初始状态逐步应用解路径中的动作，检查最终是否到达目标状态。
     */
    private static boolean verifyPath(Problem problem, Deque<Node> path) {
        PuzzleBoard state = (PuzzleBoard) problem.root().getState();
        for (Node node : path) {
            if (node.getAction() == null) {
                return false;
            }
            state = state.next(node.getAction());
        }
        return problem.goal(state);
    }

    private static ArrayList<String> lines(String... content) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : content) {
            lines.add(line);
        }
        return lines;
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("检查失败（预期抛出异常）：" + message);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }
}
