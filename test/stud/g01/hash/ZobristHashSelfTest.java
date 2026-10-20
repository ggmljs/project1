package stud.g01.hash;

import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * ZobristHash 的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：随机表可复现、相同棋盘内容得到相同哈希、每一步移动都会改变哈希、
 * 增量哈希与从头计算一致、8 数码全部 181440 个可达状态无碰撞、
 * 随机游走 10 万个 15 数码状态无碰撞、非法入参被拒绝。</p>
 */
public final class ZobristHashSelfTest {

    /** 8 数码的全部可达状态数（经典结论，用于验证枚举是否完整） */
    private static final int EIGHT_PUZZLE_STATES = 181440;

    private static int checks = 0;

    public static void main(String[] args) {
        testCachedTable();
        testDeterministicKey();
        testSameContentSameKey();
        testMoveChangesKey();
        testIncrementalMatchesFull();
        testNoCollisionOverEightPuzzle();
        testNoCollisionOverRandomFifteenPuzzle();
        testIllegalArguments();

        System.out.println("ZobristHashSelfTest: all tests passed. checks=" + checks);
    }

    private static void testCachedTable() {
        ZobristHash a = ZobristHash.forSize(3);
        ZobristHash b = ZobristHash.forSize(3);
        expect(a == b, "同一边长复用同一张随机表");
        expect(a.size() == 3, "哈希表记录棋盘边长");
        expect(a.cellCount() == 9, "3 阶棋盘的格子数为 9");
    }

    private static void testDeterministicKey() {
        PuzzleBoard board = new PuzzleBoard(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        PuzzleBoard copy = new PuzzleBoard(3, new int[]{5, 0, 8, 4, 2, 1, 7, 3, 6});
        ZobristHash hash = ZobristHash.forSize(3);
        expect(hash.keyOf(board) == hash.keyOf(copy), "内容相同的两个棋盘得到相同的哈希值");
    }

    private static void testSameContentSameKey() {
        PuzzleBoard goal = goal(4);
        PuzzleBoard sameByConstructor = new PuzzleBoard(4, goal.getTiles());
        ZobristHash hash = ZobristHash.forSize(4);
        expect(hash.keyOf(goal) == hash.keyOf(sameByConstructor),
                "4 阶棋盘：内容相同 -> 哈希相同");
    }

    private static void testMoveChangesKey() {
        PuzzleBoard board = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8});
        ZobristHash hash = ZobristHash.forSize(3);
        long key = hash.keyOf(board);

        for (PuzzleMove move : moves()) {
            if (!board.canMove(move)) {
                continue;
            }
            PuzzleBoard next = board.next(move);
            long moved = hash.afterMove(key, board, move);
            expect(moved != key, "移动 " + move + " 之后哈希值必须改变");
            expect(moved == hash.keyOf(next), "增量哈希 = 移动后棋盘从头计算的哈希");
        }
        expect(hash.keyOf(board) == key, "原棋盘不可变，哈希值保持不变");
    }

    /**
     * 随机游走：每一步都校验“增量哈希”与“从头计算”一致，3 阶和 4 阶各测一遍。
     */
    private static void testIncrementalMatchesFull() {
        Random random = new Random(20240401L);
        for (int size : new int[]{3, 4}) {
            ZobristHash hash = ZobristHash.forSize(size);
            PuzzleBoard board = goal(size);
            long key = hash.keyOf(board);

            for (int step = 0; step < 300; step++) {
                PuzzleMove move = randomLegalMove(board, random);
                long nextKey = hash.afterMove(key, board, move);
                board = board.next(move);
                expect(nextKey == hash.keyOf(board),
                        size + " 阶随机游走第 " + (step + 1) + " 步的增量哈希与全量计算一致");
                key = nextKey;
            }
        }
    }

    /**
     * 枚举 8 数码全部可达状态，验证：
     * 1) 状态数确实是 181440；2) 不同状态的 Zobrist 哈希互不相同（无碰撞）。
     */
    private static void testNoCollisionOverEightPuzzle() {
        ZobristHash hash = ZobristHash.forSize(3);
        PuzzleBoard start = goal(3);

        Set<PuzzleBoard> visited = new HashSet<>();
        Set<Long> keys = new HashSet<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        visited.add(start);
        keys.add(hash.keyOf(start));
        queue.add(start);

        while (!queue.isEmpty()) {
            PuzzleBoard board = queue.poll();
            for (PuzzleMove move : moves()) {
                if (!board.canMove(move)) {
                    continue;
                }
                PuzzleBoard next = board.next(move);
                if (!visited.add(next)) {
                    continue;
                }
                long key = hash.keyOf(next);
                if (!keys.add(key)) {
                    throw new AssertionError("检查失败：8 数码状态出现哈希碰撞：" + next);
                }
                queue.add(next);
            }
        }

        expect(visited.size() == EIGHT_PUZZLE_STATES,
                "8 数码可达状态数应为 " + EIGHT_PUZZLE_STATES + "，实际 " + visited.size());
        expect(keys.size() == visited.size(), "8 数码全部状态的哈希值互不相同");
    }

    /**
     * 随机游走 10 万个 15 数码状态，检查哈希碰撞：一旦 key 重复，比较两个棋盘内容。
     */
    private static void testNoCollisionOverRandomFifteenPuzzle() {
        ZobristHash hash = ZobristHash.forSize(4);
        Random random = new Random(20240402L);
        Map<Long, PuzzleBoard> seen = new HashMap<>();

        PuzzleBoard board = goal(4);
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            long key = hash.keyOf(board);
            PuzzleBoard previous = seen.putIfAbsent(key, board);
            if (previous != null && !previous.equals(board)) {
                throw new AssertionError("检查失败：15 数码随机游走出现哈希碰撞：" + board);
            }
            board = board.next(randomLegalMove(board, random));
        }
        expect(seen.size() > 0, "随机游走至少产生一个不同的 Zobrist 值");
    }

    private static void testIllegalArguments() {
        expectThrows(() -> ZobristHash.forSize(1), "边长 1 不被支持");
        expectThrows(() -> ZobristHash.forSize(11), "边长 11 不被支持");

        ZobristHash hash3 = ZobristHash.forSize(3);
        expectThrows(() -> hash3.keyOf(goal(4)), "用 3 阶哈希表计算 4 阶棋盘应报错");
        expectThrows(() -> hash3.keyOf(null), "棋盘为 null 应报错");
        expectThrows(() -> hash3.afterMove(0L, new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0}),
                PuzzleMove.DOWN), "越界移动应报错");
        expectThrows(() -> hash3.afterMove(0L, goal(3), null), "move 为 null 应报错");
    }

    private static PuzzleBoard goal(int size) {
        int[] tiles = new int[size * size];
        for (int i = 0; i < tiles.length - 1; i++) {
            tiles[i] = i + 1;
        }
        return new PuzzleBoard(size, tiles);
    }

    private static PuzzleMove[] moves() {
        return new PuzzleMove[]{PuzzleMove.UP, PuzzleMove.DOWN, PuzzleMove.LEFT, PuzzleMove.RIGHT};
    }

    private static PuzzleMove randomLegalMove(PuzzleBoard board, Random random) {
        PuzzleMove[] moves = moves();
        while (true) {
            PuzzleMove move = moves[random.nextInt(moves.length)];
            if (board.canMove(move)) {
                return move;
            }
        }
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("检查失败（本应抛出异常）：" + message);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }
}
