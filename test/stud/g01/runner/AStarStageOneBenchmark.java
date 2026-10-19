package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 第一阶段（A* + 优先队列）的性能测试工具（成员三负责）。
 *
 * <p>用法：</p>
 *
 * <pre>
 * # 跑 resources/problems.txt 里所有的 8 数码实例（4 阶实例会被跳过）
 * java -cp out stud.g01.runner.AStarStageOneBenchmark
 *
 * # 指定实例文件
 * java -cp out stud.g01.runner.AStarStageOneBenchmark resources/problems.txt
 *
 * # 随机生成 20 个可解的 8 数码实例（固定随机种子，结果可复现）
 * java -cp out stud.g01.runner.AStarStageOneBenchmark --random 20
 * </pre>
 *
 * <p>输出为 Markdown 表格，可直接粘进研究报告；每行给出
 * 初始状态的启发值、最优解长度、耗时、生成/扩展结点数，并校验解路径是否真的到达目标。</p>
 */
public final class AStarStageOneBenchmark {

    /** 固定随机种子，保证报告里的数据可复现 */
    private static final long RANDOM_SEED = 20240301L;

    private static final HeuristicType[] HEURISTICS = {
            HeuristicType.MISPLACED,
            HeuristicType.MANHATTAN
    };

    public static void main(String[] args) throws IOException {
        List<Problem> problems;
        String source;
        int randomCount = parseRandomCount(args);

        if (randomCount > 0) {
            problems = randomProblems(randomCount);
            source = "随机生成 " + randomCount + " 个可解 8 数码实例（seed=" + RANDOM_SEED + "，目标为 1..8 顺序排列）";
        } else {
            Path file = Path.of(args.length > 0 && !args[0].startsWith("--")
                    ? args[0] : "resources/problems.txt");
            problems = problemsFromFile(file);
            source = "实例文件 " + file;
        }

        System.out.println("# 第一阶段 A* 性能数据");
        System.out.println();
        System.out.println("数据来源：" + source);
        System.out.println();
        System.out.println("| 实例 | 启发函数 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 解校验 |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |");

        StringBuilder summary = new StringBuilder();
        summary.append("\n| 启发函数 | 实例数 | 求解成功 | 平均用时(ms) | 平均生成结点 | 平均扩展结点 |\n");
        summary.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");

        for (HeuristicType type : HEURISTICS) {
            PuzzleFeeder feeder = new PuzzleFeeder();
            Predictor predictor = feeder.getPredictor(type);

            int index = 0;
            int solved = 0;
            long totalNanos = 0;
            long totalGenerated = 0;
            long totalExpanded = 0;

            for (Problem problem : problems) {
                index++;
                AbstractSearcher searcher = feeder.getAStar(type);

                int startHeuristic = problem.root(predictor).getHeuristic();
                long start = System.nanoTime();
                Deque<Node> path = searcher.search(problem);
                long nanos = System.nanoTime() - start;

                totalNanos += nanos;
                totalGenerated += searcher.nodesGenerated();
                totalExpanded += searcher.nodesExpanded();

                String pathLength = "-";
                String verified = "无解";
                if (path != null) {
                    solved++;
                    pathLength = Integer.toString(path.size());
                    verified = verify(problem, path) ? "通过" : "失败";
                }

                System.out.printf(Locale.ROOT,
                        "| #%d | %s | %d | %s | %.3f | %d | %d | %s |%n",
                        index, type, startHeuristic, pathLength, nanos / 1_000_000.0,
                        searcher.nodesGenerated(), searcher.nodesExpanded(), verified);
            }

            summary.append(String.format(Locale.ROOT,
                    "| %s | %d | %d | %.3f | %d | %d |%n",
                    type, index, solved, totalNanos / 1_000_000.0 / Math.max(index, 1),
                    totalGenerated / Math.max(index, 1), totalExpanded / Math.max(index, 1)));
        }

        System.out.println(summary);
    }

    /**
     * 从文件读取实例，只保留 8 数码（边长 3）的实例。
     */
    private static List<Problem> problemsFromFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("找不到实例文件：" + file.toAbsolutePath());
        }

        ArrayList<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        ArrayList<Problem> parsed = new PuzzleFeeder().getProblems(lines);

        List<Problem> eightPuzzle = new ArrayList<>();
        for (Problem problem : parsed) {
            PuzzleBoard initial = (PuzzleBoard) problem.root().getState();
            if (initial.getSize() == 3) {
                eightPuzzle.add(problem);
            }
        }

        if (eightPuzzle.isEmpty()) {
            throw new IOException("实例文件里没有 8 数码（边长 3）实例：" + file.toAbsolutePath());
        }
        System.out.println("# 提示：文件中 " + parsed.size() + " 个实例，其中 8 数码 "
                + eightPuzzle.size() + " 个，4 阶实例留给阶段二/三");
        System.out.println();
        return eightPuzzle;
    }

    /**
     * 随机生成可解的 8 数码实例：洗牌初始布局，用 NPuzzleProblem.solvable() 过滤。
     */
    private static List<Problem> randomProblems(int count) {
        Random random = new Random(RANDOM_SEED);
        PuzzleBoard goal = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

        List<Problem> problems = new ArrayList<>();
        while (problems.size() < count) {
            int[] tiles = goal.getTiles();
            for (int i = tiles.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = tiles[i];
                tiles[i] = tiles[j];
                tiles[j] = tmp;
            }

            PuzzleBoard initial = new PuzzleBoard(3, tiles);
            Problem problem = new NPuzzleProblem(initial, goal, 3);
            if (problem.solvable()) {
                problems.add(problem);
            }
        }
        return problems;
    }

    /**
     * 校验解路径：从初始状态逐步执行动作，最终应到达目标状态。
     */
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

    private static int parseRandomCount(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--random".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--random 后面需要给出实例个数");
                }
                return Integer.parseInt(args[i + 1]);
            }
        }
        return 0;
    }

    private AStarStageOneBenchmark() {
    }
}
