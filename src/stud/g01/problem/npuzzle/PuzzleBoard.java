package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * N-Puzzle 的一个棋盘状态。
 *
 * <p>该类是不可变对象。执行一次移动会创建新的棋盘，原棋盘不会被修改，
 * 因而可以安全地作为 HashSet/HashMap 的键用于重复状态检测。</p>
 */
public final class PuzzleBoard extends State {

    private static final List<PuzzleMove> ALL_MOVES = List.of(
            PuzzleMove.UP,
            PuzzleMove.DOWN,
            PuzzleMove.LEFT,
            PuzzleMove.RIGHT
    );

    private final int size;
    private final int[] tiles;
    private final int blankIndex;
    private final int hashCode;

    /**
     * 创建棋盘。
     *
     * @param size  棋盘边长，例如 8-Puzzle 的边长为 3
     * @param tiles 按行展开的棋盘数据，数字 0 表示空格
     */
    public PuzzleBoard(int size, int[] tiles) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        Objects.requireNonNull(tiles, "tiles cannot be null");

        final int cellCount;
        try {
            cellCount = Math.multiplyExact(size, size);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("size is too large", e);
        }

        if (tiles.length != cellCount) {
            throw new IllegalArgumentException(
                    "tiles length must be size * size: expected "
                            + cellCount + ", actual " + tiles.length
            );
        }

        boolean[] appeared = new boolean[cellCount];
        int foundBlank = -1;
        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            if (tile < 0 || tile >= cellCount) {
                throw new IllegalArgumentException(
                        "tile value out of range at index " + i + ": " + tile
                );
            }
            if (appeared[tile]) {
                throw new IllegalArgumentException("duplicate tile value: " + tile);
            }
            appeared[tile] = true;
            if (tile == 0) {
                foundBlank = i;
            }
        }

        this.size = size;
        this.tiles = tiles.clone();
        this.blankIndex = foundBlank;
        this.hashCode = calculateHashCode(size, this.tiles);
    }

    /**
     * 根据一次已经验证过的合法移动快速创建内部状态。
     * ownedTiles 是 next() 刚创建的数组，不会被外部代码持有，因此无须再次复制和校验。
     */
    private PuzzleBoard(int size, int[] ownedTiles, int blankIndex) {
        this.size = size;
        this.tiles = ownedTiles;
        this.blankIndex = blankIndex;
        this.hashCode = calculateHashCode(size, ownedTiles);
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    /**
     * 返回采用给定动作后的新棋盘，当前棋盘保持不变。
     *
     * @throws IllegalArgumentException action 类型不正确或移动越界时抛出
     */
    @Override
    public PuzzleBoard next(Action action) {
        if (!(action instanceof PuzzleMove)) {
            throw new IllegalArgumentException("action must be a PuzzleMove");
        }

        PuzzleMove move = (PuzzleMove) action;
        if (!canMove(move)) {
            throw new IllegalArgumentException("illegal move " + move + " for current board");
        }

        int targetIndex;
        switch (move.getDirection()) {
            case UP:
                targetIndex = blankIndex - size;
                break;
            case DOWN:
                targetIndex = blankIndex + size;
                break;
            case LEFT:
                targetIndex = blankIndex - 1;
                break;
            case RIGHT:
                targetIndex = blankIndex + 1;
                break;
            default:
                throw new IllegalStateException("unsupported direction: " + move.getDirection());
        }

        int[] nextTiles = tiles.clone();
        nextTiles[blankIndex] = nextTiles[targetIndex];
        nextTiles[targetIndex] = 0;
        return new PuzzleBoard(size, nextTiles, targetIndex);
    }

    /**
     * 返回四种候选动作。动作是否适用于当前棋盘由 canMove/applicable 判断。
     */
    @Override
    public Iterable<? extends Action> actions() {
        return ALL_MOVES;
    }

    /**
     * 判断空格能否沿给定方向移动。
     */
    public boolean canMove(PuzzleMove move) {
        if (move == null) {
            return false;
        }

        switch (move.getDirection()) {
            case UP:
                return getBlankRow() > 0;
            case DOWN:
                return getBlankRow() < size - 1;
            case LEFT:
                return getBlankCol() > 0;
            case RIGHT:
                return getBlankCol() < size - 1;
            default:
                return false;
        }
    }

    public int getSize() {
        return size;
    }

    public int getBlankIndex() {
        return blankIndex;
    }

    public int getBlankRow() {
        return blankIndex / size;
    }

    public int getBlankCol() {
        return blankIndex % size;
    }

    public int getTile(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IndexOutOfBoundsException(
                    "position out of board: (" + row + ", " + col + ")"
            );
        }
        return tiles[row * size + col];
    }

    public int getTile(int index) {
        if (index < 0 || index >= tiles.length) {
            throw new IndexOutOfBoundsException("tile index out of board: " + index);
        }
        return tiles[index];
    }

    /**
     * 返回棋盘数据的副本，防止调用者修改当前状态。
     */
    public int[] getTiles() {
        return tiles.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PuzzleBoard)) {
            return false;
        }
        PuzzleBoard other = (PuzzleBoard) obj;
        return size == other.size && Arrays.equals(tiles, other.tiles);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int calculateHashCode(int size, int[] tiles) {
        return 31 * size + Arrays.hashCode(tiles);
    }

    @Override
    public String toString() {
        int width = Integer.toString(size * size - 1).length();
        StringBuilder board = new StringBuilder();

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (col > 0) {
                    board.append(' ');
                }

                int tile = tiles[row * size + col];
                String text = tile == 0 ? "_" : Integer.toString(tile);
                for (int padding = text.length(); padding < width; padding++) {
                    board.append(' ');
                }
                board.append(text);
            }

            if (row < size - 1) {
                board.append(System.lineSeparator());
            }
        }

        return board.toString();
    }
}
