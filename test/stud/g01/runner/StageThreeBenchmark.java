package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.visualization.PuzzleSolutionRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 第三阶段的轻量性能和路径展示工具。
 *
 * <p>默认读取 resources/problems8.txt，避免直接对全部 15-Puzzle 实例运行 A*。
 * 可传入 resources/problems3.txt，并用 --max-size 3 只检查 8-Puzzle。</p>
 */
public final class StageThreeBenchmark {

    public static void main(String[] args) throws IOException {
        Path input = Path.of(args.length > 0 ? args[0] : "resources/problems8.txt");
        int maxSize = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Path output = args.length > 2 ? Path.of(args[2]) : null;

        ArrayList<String> lines = new ArrayList<>(Files.readAllLines(input, StandardCharsets.UTF_8));
        PuzzleFeeder feeder = new PuzzleFeeder();
        List<Problem> problems = feeder.getProblems(lines);
        Predictor manhattanPredictor = feeder.getPredictor(HeuristicType.MANHATTAN);
        Predictor patternPredictor = feeder.getPredictor(HeuristicType.DISJOINT_PATTERN);
        StringBuilder report = new StringBuilder();
        report.append("# Stage Three Disjoint Pattern Database\n\n");
        report.append("| Instance | Size | Manhattan | Pattern DB | Solution length | Generated | Expanded | Verified |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");

        int index = 0;
        for (Problem problem : problems) {
            PuzzleBoard board = (PuzzleBoard) problem.root().getState();
            if (board.getSize() > maxSize) {
                continue;
            }
            index++;
            PuzzleBoard goal = ((NPuzzleProblem) problem).goalBoard();
            int manhattan = manhattanPredictor.heuristics(board, goal);
            AbstractSearcher searcher = feeder.getAStar(HeuristicType.DISJOINT_PATTERN);
            long start = System.nanoTime();
            Deque<Node> path = searcher.search(problem);
            double millis = (System.nanoTime() - start) / 1_000_000.0;
            boolean verified = path != null && verify(problem, path);
            int pattern = patternPredictor.heuristics(board, goal);
            report.append(String.format("| #%d | %d | %d | %d | %s | %d | %d | %s |\n",
                    index, board.getSize(), manhattan, pattern,
                    path == null ? "-" : path.size(), searcher.nodesGenerated(),
                    searcher.nodesExpanded(), verified ? "yes" : "no"));
            report.append(String.format("\nTime: %.3f ms\n\n", millis));

            if (path != null && output != null && index == 1) {
                PuzzleSolutionRenderer.write(output, board, path);
            }
        }

        System.out.print(report);
        if (output != null) {
            System.out.println("Solution written to: " + output.toAbsolutePath());
        }
    }

    private static boolean verify(Problem problem, Deque<Node> path) {
        PuzzleBoard state = (PuzzleBoard) problem.root().getState();
        for (Node node : path) {
            state = state.next(node.getAction());
        }
        return problem.goal(state);
    }

    private StageThreeBenchmark() {
    }
}
