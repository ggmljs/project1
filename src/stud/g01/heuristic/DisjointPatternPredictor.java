package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * N-Puzzle 的 Disjoint Pattern Database（不相交模式数据库）启发函数。
 *
 * <p>每个模式只跟踪一组数字和空格，把其他数字视为不可区分的背景。
 * 从目标抽象状态做 0-1 BFS：移动模式数字代价为 1，移动背景数字代价为 0。
 * 不同模式之间没有重复数字，因此各模式距离之和是可采纳的启发式函数。</p>
 *
 * <p>本实现面向课程中的 8-Puzzle 和 15-Puzzle。默认分组为：3 阶使用
 * 4+4，4 阶使用 4+4+4+3。数据库按目标棋盘缓存，同一个目标布局不会重复构建。</p>
 */
public final class DisjointPatternPredictor implements Predictor {

    private static final int PATTERN_WIDTH = 4;
    private static final int MAX_SUPPORTED_SIZE = 4;

    private static final Map<PuzzleBoard, PatternDatabaseSet> CACHE = new ConcurrentHashMap<>();

    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard board = asBoard(state, "state");
        PuzzleBoard goalBoard = asBoard(goal, "goal");
        if (board.getSize() != goalBoard.getSize()) {
            throw new IllegalArgumentException("state and goal must have the same size");
        }
        PatternDatabaseSet databases = CACHE.computeIfAbsent(
                goalBoard, PatternDatabaseSet::build);
        return databases.heuristics(board);
    }

    /** 清空缓存，便于 benchmark 或自测隔离内存。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 返回当前缓存的目标布局数量。 */
    public static int cachedGoalCount() {
        return CACHE.size();
    }

    @Override
    public String toString() {
        return "DISJOINT_PATTERN";
    }

    private static PuzzleBoard asBoard(State state, String role) {
        if (!(state instanceof PuzzleBoard)) {
            throw new IllegalArgumentException(role + " must be a PuzzleBoard");
        }
        PuzzleBoard board = (PuzzleBoard) state;
        if (board.getSize() > MAX_SUPPORTED_SIZE) {
            throw new IllegalArgumentException(
                    "DISJOINT_PATTERN currently supports at most " + MAX_SUPPORTED_SIZE + "x"
                            + MAX_SUPPORTED_SIZE + " boards");
        }
        return board;
    }

    private static final class PatternDatabaseSet {
        private final List<PatternDatabase> databases;

        private PatternDatabaseSet(List<PatternDatabase> databases) {
            this.databases = databases;
        }

        private static PatternDatabaseSet build(PuzzleBoard goal) {
            List<int[]> patterns = splitPatterns(goal);
            List<PatternDatabase> databases = new ArrayList<>(patterns.size());
            for (int[] pattern : patterns) {
                databases.add(PatternDatabase.build(goal, pattern));
            }
            return new PatternDatabaseSet(databases);
        }

        private int heuristics(PuzzleBoard board) {
            int total = 0;
            for (PatternDatabase database : databases) {
                total += database.distance(board);
            }
            return total;
        }

        private static List<int[]> splitPatterns(PuzzleBoard goal) {
            int[] tiles = new int[goal.getSize() * goal.getSize() - 1];
            int count = 0;
            for (int index = 0; index < goal.getSize() * goal.getSize(); index++) {
                int tile = goal.getTile(index);
                if (tile != 0) {
                    tiles[count++] = tile;
                }
            }

            List<int[]> patterns = new ArrayList<>();
            for (int start = 0; start < tiles.length; start += PATTERN_WIDTH) {
                patterns.add(Arrays.copyOfRange(
                        tiles, start, Math.min(start + PATTERN_WIDTH, tiles.length)));
            }
            return patterns;
        }
    }

    private static final class PatternDatabase {
        private final int size;
        private final int bits;
        private final int positionMask;
        private final int[] pattern;
        private final IntIntMap distances;

        private PatternDatabase(int size, int bits, int[] pattern, IntIntMap distances) {
            this.size = size;
            this.bits = bits;
            this.positionMask = (1 << bits) - 1;
            this.pattern = pattern;
            this.distances = distances;
        }

        private static PatternDatabase build(PuzzleBoard goal, int[] pattern) {
            int size = goal.getSize();
            int bits = bitsFor(size * size);
            if ((pattern.length + 1) * bits > 31) {
                throw new IllegalArgumentException("pattern state cannot be packed into an int");
            }

            int[] goalPositions = new int[pattern.length + 1];
            goalPositions[0] = goal.getBlankIndex();
            for (int i = 0; i < pattern.length; i++) {
                goalPositions[i + 1] = positionOf(goal, pattern[i]);
            }

            IntIntMap distances = new IntIntMap(estimateStateCount(size * size, pattern.length));
            int goalKey = encode(goalPositions, bits);
            distances.putIfLower(goalKey, 0);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(goalKey);

            int[] positions = new int[goalPositions.length];
            while (!queue.isEmpty()) {
                int key = queue.removeFirst();
                int distance = distances.getOrDefault(key, Integer.MAX_VALUE);
                decode(key, bits, positions);

                int blank = positions[0];
                int row = blank / size;
                int col = blank % size;
                for (int[] delta : DELTAS) {
                    int nextRow = row + delta[0];
                    int nextCol = col + delta[1];
                    if (nextRow < 0 || nextRow >= size || nextCol < 0 || nextCol >= size) {
                        continue;
                    }

                    int nextBlank = nextRow * size + nextCol;
                    int movedSlot = 0;
                    for (int slot = 1; slot < positions.length; slot++) {
                        if (positions[slot] == nextBlank) {
                            movedSlot = slot;
                            break;
                        }
                    }

                    positions[0] = nextBlank;
                    if (movedSlot != 0) {
                        positions[movedSlot] = blank;
                    }
                    int nextKey = encode(positions, bits);
                    int moveCost = movedSlot == 0 ? 0 : 1;
                    if (distances.putIfLower(nextKey, distance + moveCost)) {
                        if (moveCost == 0) {
                            queue.addFirst(nextKey);
                        } else {
                            queue.addLast(nextKey);
                        }
                    }

                    positions[0] = blank;
                    if (movedSlot != 0) {
                        positions[movedSlot] = nextBlank;
                    }
                }
            }

            return new PatternDatabase(size, bits, pattern.clone(), distances);
        }

        private int distance(PuzzleBoard board) {
            int[] positions = new int[pattern.length + 1];
            positions[0] = board.getBlankIndex();
            for (int i = 0; i < pattern.length; i++) {
                positions[i + 1] = positionOf(board, pattern[i]);
            }
            int result = distances.getOrDefault(encode(positions, bits), -1);
            if (result < 0) {
                throw new IllegalStateException("abstract state is missing from pattern database");
            }
            return result;
        }

        private static final int[][] DELTAS = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1}
        };

        private static int positionOf(PuzzleBoard board, int tile) {
            for (int index = 0; index < board.getSize() * board.getSize(); index++) {
                if (board.getTile(index) == tile) {
                    return index;
                }
            }
            throw new IllegalArgumentException("tile " + tile + " is not present on board");
        }

        private static int encode(int[] positions, int bits) {
            int key = 0;
            for (int i = 0; i < positions.length; i++) {
                key |= positions[i] << (i * bits);
            }
            return key;
        }

        private static void decode(int key, int bits, int[] positions) {
            int mask = (1 << bits) - 1;
            for (int i = 0; i < positions.length; i++) {
                positions[i] = (key >>> (i * bits)) & mask;
            }
        }

        private static int bitsFor(int cellCount) {
            int bits = 0;
            int capacity = 1;
            while (capacity < cellCount) {
                capacity <<= 1;
                bits++;
            }
            return bits;
        }

        private static int estimateStateCount(int cellCount, int patternSize) {
            long combinations = 1;
            for (int i = 1; i <= patternSize; i++) {
                combinations = combinations * (cellCount - patternSize + i) / i;
            }
            long factorial = 1;
            for (int i = 2; i <= patternSize + 1; i++) {
                factorial *= i;
            }
            long estimate = combinations * factorial;
            return (int) Math.min(Math.max(estimate, 64), 1_500_000);
        }
    }

    /** 一个只保存原始 int 键和值的开放寻址表，避免 PDB 使用大量装箱对象。 */
    private static final class IntIntMap {
        private int[] keys;
        private int[] values;
        private boolean[] used;
        private int mask;
        private int size;

        private IntIntMap(int expectedSize) {
            int capacity = 16;
            long required = Math.max(16L, (long) expectedSize * 2L);
            while (capacity < required && capacity < (1 << 25)) {
                capacity <<= 1;
            }
            keys = new int[capacity];
            values = new int[capacity];
            used = new boolean[capacity];
            mask = capacity - 1;
        }

        private int getOrDefault(int key, int fallback) {
            int slot = findSlot(key);
            return used[slot] ? values[slot] : fallback;
        }

        private boolean putIfLower(int key, int value) {
            if ((size + 1) * 10 >= keys.length * 7) {
                resize();
            }
            int slot = findSlot(key);
            if (!used[slot]) {
                used[slot] = true;
                keys[slot] = key;
                values[slot] = value;
                size++;
                return true;
            }
            if (value < values[slot]) {
                values[slot] = value;
                return true;
            }
            return false;
        }

        private int findSlot(int key) {
            int hash = key;
            hash ^= hash >>> 16;
            hash *= 0x7feb352d;
            hash ^= hash >>> 15;
            hash *= 0x846ca68b;
            hash ^= hash >>> 16;
            int slot = hash & mask;
            while (used[slot] && keys[slot] != key) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        private void resize() {
            int[] oldKeys = keys;
            int[] oldValues = values;
            boolean[] oldUsed = used;
            keys = new int[oldKeys.length << 1];
            values = new int[oldValues.length << 1];
            used = new boolean[oldUsed.length << 1];
            mask = keys.length - 1;
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldUsed[i]) {
                    int slot = findSlot(oldKeys[i]);
                    used[slot] = true;
                    keys[slot] = oldKeys[i];
                    values[slot] = oldValues[i];
                    size++;
                }
            }
        }
    }
}
