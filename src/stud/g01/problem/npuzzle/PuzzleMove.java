package stud.g01.problem.npuzzle;

import core.problem.Action;

import java.util.Objects;

/**
 * N-Puzzle 中空格的一次移动。
 *
 * <p>方向统一表示空格（数字 0）的移动方向，而不是数字块的移动方向。</p>
 */
public final class PuzzleMove extends Action {

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    public static final PuzzleMove UP = new PuzzleMove(Direction.UP);
    public static final PuzzleMove DOWN = new PuzzleMove(Direction.DOWN);
    public static final PuzzleMove LEFT = new PuzzleMove(Direction.LEFT);
    public static final PuzzleMove RIGHT = new PuzzleMove(Direction.RIGHT);

    private final Direction direction;

    public PuzzleMove(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction cannot be null");
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    /**
     * N-Puzzle 中每次合法移动的代价均为 1。
     */
    @Override
    public int stepCost() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PuzzleMove)) {
            return false;
        }
        PuzzleMove other = (PuzzleMove) obj;
        return direction == other.direction;
    }

    @Override
    public int hashCode() {
        return direction.hashCode();
    }

    @Override
    public String toString() {
        return direction.name();
    }
}
