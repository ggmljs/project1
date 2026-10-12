package stud.g01.problem.npuzzle;

import core.problem.Action;

import java.util.HashSet;
import java.util.Set;

/**
 * 不依赖第三方测试框架的 PuzzleBoard 基础测试。
 */
public final class PuzzleBoardSelfTest {

    private PuzzleBoardSelfTest() {
    }

    public static void main(String[] args) {
        testMovementAndImmutability();
        testAllDirectionsAndInverseMoves();
        testBoundaryMoves();
        testEqualityAndHashCode();
        testDefensiveCopies();
        testActions();
        testTextDrawing();
        testFourByFourBoard();
        testInvalidBoard();
        System.out.println("PuzzleBoardSelfTest: all tests passed.");
    }

    private static void testMovementAndImmutability() {
        PuzzleBoard board = boardWithCenterBlank();

        PuzzleBoard movedUp = (PuzzleBoard) board.next(PuzzleMove.UP);
        check(movedUp.equals(new PuzzleBoard(3, new int[]{
                1, 0, 3,
                4, 2, 5,
                6, 7, 8
        })), "UP should move the blank upward");

        check(board.equals(boardWithCenterBlank()),
                "next() must not modify the original board");
        check(movedUp.getBlankRow() == 0 && movedUp.getBlankCol() == 1,
                "blank position should be updated after a move");
    }

    private static void testAllDirectionsAndInverseMoves() {
        PuzzleBoard board = boardWithCenterBlank();

        check(board.next(PuzzleMove.UP).equals(new PuzzleBoard(3, new int[]{
                1, 0, 3,
                4, 2, 5,
                6, 7, 8
        })), "UP result is incorrect");
        check(board.next(PuzzleMove.DOWN).equals(new PuzzleBoard(3, new int[]{
                1, 2, 3,
                4, 7, 5,
                6, 0, 8
        })), "DOWN result is incorrect");
        check(board.next(PuzzleMove.LEFT).equals(new PuzzleBoard(3, new int[]{
                1, 2, 3,
                0, 4, 5,
                6, 7, 8
        })), "LEFT result is incorrect");
        check(board.next(PuzzleMove.RIGHT).equals(new PuzzleBoard(3, new int[]{
                1, 2, 3,
                4, 5, 0,
                6, 7, 8
        })), "RIGHT result is incorrect");

        check(board.next(PuzzleMove.UP).next(PuzzleMove.DOWN).equals(board),
                "UP followed by DOWN should restore the board");
        check(board.next(PuzzleMove.LEFT).next(PuzzleMove.RIGHT).equals(board),
                "LEFT followed by RIGHT should restore the board");
    }

    private static void testBoundaryMoves() {
        PuzzleBoard topLeftBlank = new PuzzleBoard(3, new int[]{
                0, 1, 2,
                3, 4, 5,
                6, 7, 8
        });

        check(!topLeftBlank.canMove(PuzzleMove.UP), "UP must be illegal at the top edge");
        check(!topLeftBlank.canMove(PuzzleMove.LEFT), "LEFT must be illegal at the left edge");
        check(topLeftBlank.canMove(PuzzleMove.DOWN), "DOWN should be legal at the top edge");
        check(topLeftBlank.canMove(PuzzleMove.RIGHT), "RIGHT should be legal at the left edge");

        expectIllegalArgument(() -> topLeftBlank.next(PuzzleMove.UP),
                "next() should reject an illegal move");
    }

    private static void testEqualityAndHashCode() {
        PuzzleBoard first = boardWithCenterBlank();
        PuzzleBoard second = boardWithCenterBlank();

        check(first != second, "test boards should be different objects");
        check(first.equals(second), "boards with equal tiles should be equal");
        check(first.hashCode() == second.hashCode(),
                "equal boards must have equal hash codes");

        Set<PuzzleBoard> explored = new HashSet<>();
        explored.add(first);
        explored.add(second);
        check(explored.size() == 1, "HashSet should remove duplicate board states");
    }

    private static void testDefensiveCopies() {
        int[] input = {
                1, 2, 3,
                4, 0, 5,
                6, 7, 8
        };
        PuzzleBoard board = new PuzzleBoard(3, input);

        input[0] = 8;
        check(board.getTile(0, 0) == 1,
                "changing the constructor array must not change the board");

        int[] exported = board.getTiles();
        exported[0] = 8;
        check(board.getTile(0, 0) == 1,
                "changing getTiles() result must not change the board");
    }

    private static void testActions() {
        int count = 0;
        for (Action ignored : boardWithCenterBlank().actions()) {
            count++;
        }
        check(count == 4, "actions() should expose four candidate moves");
    }

    private static void testTextDrawing() {
        String newline = System.lineSeparator();
        String expected = "1 2 3" + newline
                + "4 _ 5" + newline
                + "6 7 8";
        check(boardWithCenterBlank().toString().equals(expected),
                "toString() should draw the 3x3 board row by row");
    }

    private static void testFourByFourBoard() {
        PuzzleBoard board = new PuzzleBoard(4, new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 0, 15
        });

        check(board.getSize() == 4, "4x4 board size is incorrect");
        check(board.getBlankRow() == 3 && board.getBlankCol() == 2,
                "4x4 blank position is incorrect");
        check(board.canMove(PuzzleMove.RIGHT),
                "blank should be able to move right on the 4x4 board");
        check(board.next(PuzzleMove.RIGHT).getTile(3, 3) == 0,
                "4x4 move result is incorrect");
        check(board.toString().contains("10"),
                "4x4 text drawing should support two-digit tiles");
    }

    private static void testInvalidBoard() {
        expectIllegalArgument(() -> new PuzzleBoard(3, new int[]{
                0, 1, 2,
                3, 4, 5,
                6, 7, 7
        }), "constructor should reject duplicate tiles");
    }

    private static PuzzleBoard boardWithCenterBlank() {
        return new PuzzleBoard(3, new int[]{
                1, 2, 3,
                4, 0, 5,
                6, 7, 8
        });
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
