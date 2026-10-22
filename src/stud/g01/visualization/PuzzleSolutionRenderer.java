package stud.g01.visualization;

import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Objects;

/**
 * N-Puzzle 解路径的可复用文本渲染器。
 *
 * <p>输出包含初始棋盘、每一步动作和移动后的棋盘，既能直接打印到控制台，
 * 也能写入 UTF-8 文本文件，作为后续 Unity/SFML 可视化的输入。</p>
 */
public final class PuzzleSolutionRenderer {

    private PuzzleSolutionRenderer() {
    }

    public static String render(PuzzleBoard initial, Deque<Node> path) {
        Objects.requireNonNull(initial, "initial cannot be null");
        Objects.requireNonNull(path, "path cannot be null");

        StringBuilder output = new StringBuilder();
        output.append("N-Puzzle solution").append(System.lineSeparator());
        output.append("Step 0:").append(System.lineSeparator());
        output.append(initial).append(System.lineSeparator());

        int step = 0;
        for (Node node : path) {
            if (!(node.getState() instanceof PuzzleBoard)) {
                throw new IllegalArgumentException("path contains a non-PuzzleBoard state");
            }
            step++;
            output.append(System.lineSeparator())
                    .append("Step ").append(step)
                    .append(" (action: ").append(node.getAction()).append("):")
                    .append(System.lineSeparator())
                    .append(node.getState())
                    .append(System.lineSeparator());
        }
        output.append(System.lineSeparator())
                .append("Total steps: ").append(step)
                .append(System.lineSeparator());
        return output.toString();
    }

    public static void write(Path outputFile, PuzzleBoard initial, Deque<Node> path)
            throws IOException {
        Objects.requireNonNull(outputFile, "outputFile cannot be null");
        Files.write(outputFile, render(initial, path).getBytes(StandardCharsets.UTF_8));
    }
}
