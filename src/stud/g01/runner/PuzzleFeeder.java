package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.heuristic.ManhattanPredictor;
import stud.g01.heuristic.MisplacedTilesPredictor;
import stud.g01.heuristic.DisjointPatternPredictor;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * N-Puzzle 的素材提供者（成员三负责）。
 *
 * <p>框架通过本类拿到三样东西：问题实例列表、Frontier（Open 表）、
 * 启发函数，然后拼装出 A*（BestFirstSearcher）、Dijkstra 和 IDA*。</p>
 *
 * <p>输入文件（resources/problems.txt）每行一个实例，格式为：</p>
 *
 * <pre>
 * 边长  初始布局的 n*n 个数  目标布局的 n*n 个数
 * </pre>
 *
 * <p>数字按行展开，0 表示空格，例如：</p>
 *
 * <pre>
 * 3 5 0 8 4 2 1 7 3 6 1 2 3 4 5 6 7 8 0
 * </pre>
 */
public class PuzzleFeeder extends EngineFeeder {

    /**
     * 解析问题实例文本。空行和以 # 开头的行会被跳过。
     *
     * @param problemLines 输入文件的每一行
     * @return 解析得到的 N-Puzzle 实例列表，顺序与文件顺序一致
     * @throws IllegalArgumentException 某行格式错误或没有任何实例时抛出
     */
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        if (problemLines == null) {
            throw new IllegalArgumentException("problemLines cannot be null");
        }

        ArrayList<Problem> problems = new ArrayList<>();
        int lineNumber = 0;
        for (String line : problemLines) {
            lineNumber++;
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            problems.add(parseProblem(trimmed, lineNumber));
        }

        if (problems.isEmpty()) {
            throw new IllegalArgumentException("no problem instance found in input lines");
        }
        return problems;
    }

    /**
     * 把一行文本解析成一个 NPuzzleProblem。
     */
    private Problem parseProblem(String line, int lineNumber) {
        String[] fields = line.split("\\s+");
        int[] values = new int[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try {
                values[i] = Integer.parseInt(fields[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "第 " + lineNumber + " 行第 " + (i + 1) + " 个字段不是整数：" + fields[i], e);
            }
        }

        int size = values[0];
        if (size < 2) {
            throw new IllegalArgumentException(
                    "第 " + lineNumber + " 行的边长非法：" + size + "（至少为 2）");
        }

        int cellCount = size * size;
        int expected = 1 + 2 * cellCount;
        if (values.length != expected) {
            throw new IllegalArgumentException(
                    "第 " + lineNumber + " 行字段个数错误：边长 " + size + " 需要 "
                            + expected + " 个数字（1 + 2*" + cellCount + "），实际 " + values.length + " 个");
        }

        int[] initialTiles = Arrays.copyOfRange(values, 1, 1 + cellCount);
        int[] goalTiles = Arrays.copyOfRange(values, 1 + cellCount, 1 + 2 * cellCount);

        PuzzleBoard initial = new PuzzleBoard(size, initialTiles);
        PuzzleBoard goal = new PuzzleBoard(size, goalTiles);
        return new NPuzzleProblem(initial, goal, size);
    }

    /**
     * 按框架给出的估值机制生成 Frontier（Open 表）。
     *
     * <p>三种机制分别对应 A*（FULL）、Dijkstra（PATH_COST）
     * 和贪心最佳优先（HEURISTIC）。</p>
     */
    @Override
    public Frontier getFrontier(EvaluationType type) {
        if (type == null) {
            throw new IllegalArgumentException("evaluation type cannot be null");
        }
        Comparator<Node> comparator = Node.evaluator(type);
        if (comparator == null) {
            throw new IllegalArgumentException("unsupported evaluation type: " + type);
        }
        return new PqFrontier(comparator);
    }

    /**
     * 按启发函数类型生成 Predictor。
     *
     * <p>第一阶段：MISPLACED、MANHATTAN（本成员实现）。
     * 第三阶段：DISJOINT_PATTERN（成员五负责，这里留出接入点）。</p>
     */
    @Override
    public Predictor getPredictor(HeuristicType type) {
        if (type == null) {
            throw new IllegalArgumentException("heuristic type cannot be null");
        }
        switch (type) {
            case MISPLACED:
                return new MisplacedTilesPredictor();
            case MANHATTAN:
                return new ManhattanPredictor();
            case DISJOINT_PATTERN:
                return new DisjointPatternPredictor();
            default:
                throw new IllegalArgumentException("unsupported heuristic type for N-Puzzle: " + type);
        }
    }
}
