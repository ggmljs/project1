package stud.g01.heuristic;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.runner.PuzzleFeeder;

import java.util.ArrayList;
import java.util.Deque;

import static core.solver.algorithm.heuristic.HeuristicType.DISJOINT_PATTERN;

/** Disjoint Pattern Database 的无 JUnit 自测。 */
public final class DisjointPatternPredictorSelfTest {

    private static int checks;

    public static void main(String[] args) {
        DisjointPatternPredictor.clearCache();
        Predictor predictor = new DisjointPatternPredictor();
        PuzzleBoard goal = goal();

        expect(predictor.heuristics(goal, goal) == 0, "goal heuristic is zero");
        expect(predictor.heuristics(
                new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8}), goal) == 1,
                "one move state has heuristic one");

        PuzzleBoard fifteenGoal = new PuzzleBoard(4, new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 0
        });
        expect(predictor.heuristics(fifteenGoal, fifteenGoal) == 0,
                "15-puzzle pattern databases can be built");
        expect(predictor.heuristics(new PuzzleBoard(4, new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 0, 15
        }), fifteenGoal) == 1, "15-puzzle one move state has heuristic one");

        PuzzleFeeder feeder = new PuzzleFeeder();
        Problem problem = feeder.getProblems(lines(
                "3 5 0 8 4 2 1 7 3 6 1 2 3 4 5 6 7 8 0"
        )).get(0);
        expect(feeder.getPredictor(DISJOINT_PATTERN) instanceof DisjointPatternPredictor,
                "PuzzleFeeder exposes the pattern predictor");

        AbstractSearcher searcher = feeder.getAStar(DISJOINT_PATTERN);
        Deque<Node> path = searcher.search(problem);
        expect(path != null, "pattern database A* finds a solution");
        expect(path.size() == 21, "pattern database A* returns the optimal 21-step solution");
        expect(verify(problem, path), "returned path reaches the goal");
        expect(DisjointPatternPredictor.cachedGoalCount() == 2,
                "databases are cached for both goal layouts");

        System.out.println("DisjointPatternPredictorSelfTest: all tests passed. checks=" + checks);
    }

    private static PuzzleBoard goal() {
        return new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
    }

    private static boolean verify(Problem problem, Deque<Node> path) {
        PuzzleBoard board = (PuzzleBoard) problem.root().getState();
        for (Node node : path) {
            board = board.next(node.getAction());
        }
        return problem.goal(board);
    }

    private static ArrayList<String> lines(String... values) {
        ArrayList<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private DisjointPatternPredictorSelfTest() {
    }
}
