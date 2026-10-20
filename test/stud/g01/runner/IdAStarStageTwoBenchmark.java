package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.solver.IdAStar.Options;
import stud.queue.StackFrontier;

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
 * 第二阶段（IDA*）的性能测试工具（成员四负责）。
 *
 * <p>用法：</p>
 *
 * <pre>
 * # 默认：8 数码 A* 与 IDA* 对比 + resources/problems15.txt 的 15 数码
 * java -cp out stud.g01.runner.IdAStarStageTwoBenchmark
 *
 * # 指定 15 数码实例文件与单实例时间上限（秒）
 * java -cp out stud.g01.runner.IdAStarStageTwoBenchmark --file15 resources/problems15.txt --timeout 120
 *
 * # 额外做“有/无转置表”的消融对比
 * java -cp out stud.g01.runner.IdAStarStageTwoBenchmark --ablation
 *
 * # 用随机生成的 8 数码实例做 A* 与 IDA* 的对比（固定种子，可复现）
 * java -cp out stud.g01.runner.IdAStarStageTwoBenchmark --random8 10
 * </pre>
 *
 * <p>输出为 Markdown 表格，可直接粘进研究报告。每一行都校验解路径是否真的
 * 从初始状态到达目标；15 数码的超时实例会标出“超时”，并给出已经生成的结点数。</p>
 */
public final class IdAStarStageTwoBenchmark {

    /** 固定随机种子，保证随机实例可复现 */
    private static final long RANDOM_SEED = 20240404L;

    /** 默认的转置表大小：2^22 个槽位，约 64 MiB */
    private static final int TABLE_BITS = 22;

    /** 8 数码状态空间只有 18 万，用小表即可，避免 64 MiB 的分配开销影响对照 */
    private static final int EIGHT_PUZZLE_TABLE_BITS = 18;

    public static void main(String[] args) throws IOException {
        Path file8 = Path.of("resources/problems8.txt");
        Path file15 = Path.of("resources/problems15.txt");
        long timeoutSeconds = 60;
        int random8 = 0;
        boolean ablation = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file8":
                    file8 = Path.of(requireValue(args, ++i, "--file8"));
                    break;
                case "--file15":
                    file15 = Path.of(requireValue(args, ++i, "--file15"));
                    break;
                case "--timeout":
                    timeoutSeconds = Long.parseLong(requireValue(args, ++i, "--timeout"));
                    break;
                case "--random8":
                    random8 = Integer.parseInt(requireValue(args, ++i, "--random8"));
                    break;
                case "--ablation":
                    ablation = true;
                    break;
                default:
                    throw new IllegalArgumentException("未知参数：" + args[i]);
            }
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("--timeout 必须为正整数");
        }

        List<Problem> eightPuzzle;
        List<Problem> fifteenPuzzle;

        System.out.println("# 第二阶段（IDA*）性能数据");
        System.out.println();
        System.out.println("启发函数：曼哈顿距离；IDA* 启用 Zobrist 路径去环与转置表"
                + "（8 数码 2^" + EIGHT_PUZZLE_TABLE_BITS + "、15 数码 2^" + TABLE_BITS
                + " 槽位）；单实例时间上限 " + timeoutSeconds + " s。");
        System.out.println();

        eightPuzzle = random8 > 0 ? randomEightPuzzle(random8) : load(file8, 3);
        fifteenPuzzle = load(file15, 4);

        List<Run> eightRuns = runEightPuzzle(eightPuzzle);
        List<Run> fifteenRuns = runFifteenPuzzle(fifteenPuzzle, timeoutSeconds);
        printSummary(eightRuns, fifteenRuns, timeoutSeconds);

        if (ablation) {
            printAblation(eightPuzzle, fifteenRuns, timeoutSeconds);
        }
    }

    /**
     * 8 数码：同一实例分别用 A*（框架 BestFirstSearcher + PqFrontier）和 IDA* 求解。
     */
    private static List<Run> runEightPuzzle(List<Problem> problems) {
        PuzzleFeeder feeder = new PuzzleFeeder();
        Predictor predictor = feeder.getPredictor(HeuristicType.MANHATTAN);

        System.out.println("## 1. 8 数码：A* 与 IDA* 对比");
        System.out.println();
        System.out.println("| 实例 | 搜索 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 迭代/转置命中 | 解校验 |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |");

        List<Run> runs = new ArrayList<>();
        int index = 0;
        for (Problem problem : problems) {
            index++;
            int h0 = problem.root(predictor).getHeuristic();

            Run astar = solve(problem, feeder.getAStar(HeuristicType.MANHATTAN), predictor, "A*", h0);
            printRun(index, astar, "-");
            runs.add(astar);

            Run ida = solve(problem, new stud.solver.IdAStar(new StackFrontier(), predictor,
                    new Options(true, EIGHT_PUZZLE_TABLE_BITS, 0)), predictor, "IDA*", h0);
            printRun(index, ida, ida.extra());
            runs.add(ida);
        }
        System.out.println();
        return runs;
    }

    /**
     * 15 数码：只用 IDA*，每个实例带时间上限。
     */
    private static List<Run> runFifteenPuzzle(List<Problem> problems, long timeoutSeconds) {
        PuzzleFeeder feeder = new PuzzleFeeder();
        Predictor predictor = feeder.getPredictor(HeuristicType.MANHATTAN);

        System.out.println("## 2. 15 数码：IDA*（单实例上限 " + timeoutSeconds + " s）");
        System.out.println();
        System.out.println("| 实例 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 迭代轮数 | 转置命中 | 状态 |");
        System.out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |");

        List<Run> runs = new ArrayList<>();
        int index = 0;
        for (Problem problem : problems) {
            index++;
            int h0 = problem.root(predictor).getHeuristic();
            Run run = solve(problem, new stud.solver.IdAStar(new StackFrontier(), predictor,
                    new Options(true, TABLE_BITS, timeoutSeconds * 1000)), predictor, "IDA*", h0);
            runs.add(run);

            String length = run.path == null ? "-" : Integer.toString(run.path.size());
            String status = run.timedOut
                    ? "超时(>" + timeoutSeconds + "s)"
                    : (run.path == null ? "无解" : (run.verified ? "通过" : "失败"));
            System.out.printf(Locale.ROOT,
                    "| #%d | %d | %s | %.1f | %d | %d | %d | %d | %s |%n",
                    index, run.h0, length, run.millis,
                    run.generated, run.expanded, run.iterations, run.transpositionHits, status);
        }
        System.out.println();
        return runs;
    }

    /**
     * 打印汇总：8 数码 A* / IDA* 的规模对比，以及 15 数码的求解情况。
     */
    private static void printSummary(List<Run> eightRuns, List<Run> fifteenRuns, long timeoutSeconds) {
        System.out.println("## 3. 汇总");
        System.out.println();
        System.out.println("| 数据组 | 实例数 | 成功 | 平均用时(ms) | 平均生成结点 | 平均扩展结点 |");
        System.out.println("| --- | ---: | ---: | ---: | ---: | ---: |");

        printGroup("8 数码 A*", only(eightRuns, "A*"));
        printGroup("8 数码 IDA*", only(eightRuns, "IDA*"));
        printGroup("15 数码 IDA*（上限 " + timeoutSeconds + " s）", fifteenRuns);
        System.out.println();
    }

    private static void printGroup(String name, List<Run> runs) {
        int solved = 0;
        double totalMillis = 0;
        long totalGenerated = 0;
        long totalExpanded = 0;
        for (Run run : runs) {
            if (run.path != null) {
                solved++;
            }
            totalMillis += run.millis;
            totalGenerated += run.generated;
            totalExpanded += run.expanded;
        }
        int count = Math.max(runs.size(), 1);
        System.out.printf(Locale.ROOT, "| %s | %d | %d | %.1f | %d | %d |%n",
                name, runs.size(), solved, totalMillis / count,
                totalGenerated / count, totalExpanded / count);
    }

    /**
     * 消融实验：同一实例分别启用、关闭转置表，比较生成结点数与用时。
     */
    private static void printAblation(List<Problem> eightPuzzle, List<Run> fifteenRuns, long timeoutSeconds) {
        PuzzleFeeder feeder = new PuzzleFeeder();
        Predictor predictor = feeder.getPredictor(HeuristicType.MANHATTAN);

        System.out.println("## 4. 消融：转置表（Zobrist 去重）的作用");
        System.out.println();
        System.out.println("| 实例 | 转置表 | 生成结点 | 用时(ms) | 解长 | 结点减少 |");
        System.out.println("| --- | --- | ---: | ---: | ---: | ---: |");

        List<Problem> samples = new ArrayList<>(eightPuzzle);
        for (Run run : fifteenRuns) {
            // 只对第二阶段能在较短时间内解出的实例做消融，避免整份数据跑太久
            if (run.path != null && run.millis <= 20_000) {
                samples.add(run.problem);
            }
        }

        int eightIndex = 0;
        int fifteenIndex = 0;
        for (Problem problem : samples) {
            boolean isFifteen = ((PuzzleBoard) problem.root().getState()).getSize() == 4;
            String name;
            if (isFifteen) {
                fifteenIndex++;
                name = "15 数码 #" + fifteenIndex;
            } else {
                eightIndex++;
                name = "8 数码 #" + eightIndex + "（h0=" + problem.root(predictor).getHeuristic() + "）";
            }

            int bits = isFifteen ? TABLE_BITS : EIGHT_PUZZLE_TABLE_BITS;
            Run on = solve(problem, new stud.solver.IdAStar(new StackFrontier(), predictor,
                    new Options(true, bits, timeoutSeconds * 1000)), predictor, "IDA*(表)", 0);
            Run off = solve(problem, new stud.solver.IdAStar(new StackFrontier(), predictor,
                    new Options(false, bits, timeoutSeconds * 1000)), predictor, "IDA*(无表)", 0);

            String length = on.path == null ? "-" : Integer.toString(on.path.size());
            double speedup = off.generated == 0 ? 1.0 : (double) off.generated / on.generated;
            System.out.printf(Locale.ROOT, "| %s | 有 | %d | %.1f | %s | %.2fx |%n",
                    name, on.generated, on.millis, length, speedup);
            System.out.printf(Locale.ROOT, "| %s | 无 | %d | %.1f | %s | - |%n",
                    name, off.generated, off.millis,
                    off.path == null ? "-" : Integer.toString(off.path.size()));
        }
        System.out.println();
    }

    private static Run solve(Problem problem, AbstractSearcher searcher, Predictor predictor,
                             String name, int h0) {
        long start = System.nanoTime();
        Deque<Node> path = searcher.search(problem);
        long nanos = System.nanoTime() - start;

        Run run = new Run();
        run.problem = problem;
        run.searcher = searcher;
        run.name = name;
        run.h0 = h0;
        run.path = path;
        run.millis = nanos / 1_000_000.0;
        run.generated = searcher.nodesGenerated();
        run.expanded = searcher.nodesExpanded();
        run.verified = path != null && verify(problem, path);
        run.timedOut = searcher instanceof stud.g01.solver.IdAStar ida && ida.timedOut();
        if (searcher instanceof stud.g01.solver.IdAStar ida) {
            run.iterations = ida.iterations();
            run.transpositionHits = ida.transpositionHits();
            run.cyclePrunes = ida.cyclePrunes();
        }
        return run;
    }

    private static void printRun(int index, Run run, String extra) {
        String length = run.path == null ? "-" : Integer.toString(run.path.size());
        String verified = run.path == null ? "-" : (run.verified ? "通过" : "失败");
        System.out.printf(Locale.ROOT,
                "| #%d | %s | %d | %s | %.3f | %d | %d | %s | %s |%n",
                index, run.name, run.h0, length, run.millis,
                run.generated, run.expanded, extra, verified);
    }

    /**
     * 把实例文件解析成指定边长的实例列表。
     */
    private static List<Problem> load(Path file, int size) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("找不到实例文件：" + file.toAbsolutePath());
        }
        ArrayList<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        ArrayList<Problem> parsed = new PuzzleFeeder().getProblems(lines);

        List<Problem> problems = new ArrayList<>();
        for (Problem problem : parsed) {
            PuzzleBoard board = (PuzzleBoard) problem.root().getState();
            if (board.getSize() == size) {
                problems.add(problem);
            }
        }
        if (problems.isEmpty()) {
            throw new IOException("实例文件里没有边长 " + size + " 的实例：" + file.toAbsolutePath());
        }
        System.out.println("# 实例来源：" + file + "（共 " + parsed.size() + " 个，取其中 "
                + problems.size() + " 个边长 " + size + " 的实例）");
        System.out.println();
        return problems;
    }

    /**
     * 随机生成可解的 8 数码实例（固定种子）。
     */
    private static List<Problem> randomEightPuzzle(int count) {
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
            Problem problem = new NPuzzleProblem(new PuzzleBoard(3, tiles), goal, 3);
            if (problem.solvable()) {
                problems.add(problem);
            }
        }
        System.out.println("# 实例来源：随机生成 " + count + " 个可解 8 数码实例（seed=" + RANDOM_SEED + "）");
        System.out.println();
        return problems;
    }

    private static List<Run> only(List<Run> runs, String name) {
        List<Run> filtered = new ArrayList<>();
        for (Run run : runs) {
            if (run.name.equals(name)) {
                filtered.add(run);
            }
        }
        return filtered;
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

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " 后面需要给出参数值");
        }
        return args[index];
    }

    /**
     * 一次求解的结果。
     */
    private static final class Run {
        Problem problem;
        AbstractSearcher searcher;
        String name;
        int h0;
        Deque<Node> path;
        double millis;
        int generated;
        int expanded;
        int iterations;
        long transpositionHits;
        long cyclePrunes;
        boolean verified;
        boolean timedOut;

        String extra() {
            return iterations + "/" + transpositionHits;
        }
    }

    private IdAStarStageTwoBenchmark() {
    }
}
