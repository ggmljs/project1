package stud.g01.queue;

import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * PqFrontier 的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：三种估值机制的出队顺序、同状态去重（保留更好的结点）、
 * contains 的状态语义、clear 与索引一致性。</p>
 */
public final class PqFrontierSelfTest {

    private static int checks = 0;

    public static void main(String[] args) {
        testFullOrdering();
        testPathCostOrdering();
        testHeuristicOrdering();
        testDuplicateStateKeepsBetter();
        testDuplicateStateDropsWorse();
        testContainsUsesState();
        testClearAndIndexConsistency();

        System.out.println("PqFrontierSelfTest: all tests passed. checks=" + checks);
    }

    private static void testFullOrdering() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));

        // f 相同时取 g 更小的结点先出队（与框架 Node.evaluator 的约定一致）
        frontier.offer(node(0, 3, 1));   // f = 4, g = 3
        frontier.offer(node(1, 2, 2));   // f = 4, g = 2
        frontier.offer(node(2, 1, 1));   // f = 2

        expect(frontier.poll().getPathCost() == 1, "FULL: 最小 f 先出队");
        expect(frontier.poll().getPathCost() == 2, "FULL: f 相同取 g 小的先出队");
        expect(frontier.poll().getPathCost() == 3, "FULL: 最后出队的是 f 最大的");
        expect(frontier.isEmpty(), "FULL: 全部出队后为空");
    }

    private static void testPathCostOrdering() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.PATH_COST));
        frontier.offer(node(0, 5, 100));
        frontier.offer(node(1, 2, 100));
        frontier.offer(node(2, 9, 100));

        expect(frontier.poll().getPathCost() == 2, "PATH_COST: 按 g 升序出队");
        expect(frontier.poll().getPathCost() == 5, "PATH_COST: 第二小 g");
        expect(frontier.poll().getPathCost() == 9, "PATH_COST: 最大 g 最后");
    }

    private static void testHeuristicOrdering() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.HEURISTIC));
        frontier.offer(node(0, 100, 8));
        frontier.offer(node(1, 100, 3));
        frontier.offer(node(2, 100, 5));

        expect(frontier.poll().getHeuristic() == 3, "HEURISTIC: 按 h 升序出队");
        expect(frontier.poll().getHeuristic() == 5, "HEURISTIC: 中间 h");
        expect(frontier.poll().getHeuristic() == 8, "HEURISTIC: 最大 h 最后");
    }

    private static void testDuplicateStateKeepsBetter() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));
        PuzzleBoard state = board(1, 2, 3, 4, 0, 5, 6, 7, 8);

        // 第一次插入：f = 5
        expect(frontier.offer(new Node(state, null, null, 5, 0)), "重复状态：首次插入成功");
        // 第二次插入同状态但 f = 3，应当替换旧结点
        expect(frontier.offer(new Node(state, null, null, 3, 0)), "重复状态：更好的结点应替换旧结点");
        expect(frontier.size() == 1, "重复状态：Frontier 中只保留一个结点");
        expect(frontier.indexedSize() == 1, "重复状态：状态索引大小同步为 1");

        Node polled = frontier.poll();
        expect(polled.getPathCost() == 3, "重复状态：留下的是 f 更小的结点（g = 3）");
        expect(frontier.isEmpty(), "重复状态：poll 之后为空");
        expect(frontier.indexedSize() == 0, "重复状态：poll 之后索引同步清空");
    }

    private static void testDuplicateStateDropsWorse() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));
        PuzzleBoard state = board(1, 2, 3, 4, 0, 5, 6, 7, 8);

        frontier.offer(new Node(state, null, null, 3, 0));
        expect(!frontier.offer(new Node(state, null, null, 7, 0)), "重复状态：更差的结点应被丢弃");
        expect(frontier.size() == 1, "重复状态：丢弃后大小仍为 1");
        expect(frontier.poll().getPathCost() == 3, "重复状态：保留的仍是更好的结点");
    }

    private static void testContainsUsesState() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));
        PuzzleBoard state = board(1, 2, 3, 4, 0, 5, 6, 7, 8);
        frontier.offer(new Node(state, null, null, 4, 0));

        // 不同的 Node 对象，但状态相同：contains 应为 true
        expect(frontier.contains(new Node(board(1, 2, 3, 4, 0, 5, 6, 7, 8), null, null, 9, 0)),
                "contains：按状态相等判断，而不是按对象引用");
        expect(!frontier.contains(new Node(board(1, 2, 3, 4, 5, 0, 6, 7, 8), null, null, 4, 0)),
                "contains：不同状态返回 false");
        expect(!frontier.contains(null), "contains：入参 null 返回 false");
    }

    private static void testClearAndIndexConsistency() {
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));
        frontier.offer(node(0, 1, 0));
        frontier.offer(node(1, 2, 0));
        expect(frontier.indexedSize() == frontier.size(), "索引大小与 Frontier 大小一致");

        frontier.clear();
        expect(frontier.isEmpty(), "clear：清空 Frontier");
        expect(frontier.indexedSize() == 0, "clear：同时清空状态索引");
        expect(!frontier.contains(node(0, 1, 0)), "clear：contains 全部为 false");
    }

    private static Node node(int stateSeed, int pathCost, int heuristic) {
        // 用不同的棋盘区分状态，避免相互覆盖
        PuzzleBoard state = board(
                stateSeed % 9,
                (stateSeed + 1) % 9,
                (stateSeed + 2) % 9,
                (stateSeed + 3) % 9,
                (stateSeed + 4) % 9,
                (stateSeed + 5) % 9,
                (stateSeed + 6) % 9,
                (stateSeed + 7) % 9,
                (stateSeed + 8) % 9
        );
        return new Node(state, null, null, pathCost, heuristic);
    }

    private static PuzzleBoard board(int... tiles) {
        return new PuzzleBoard(3, tiles);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }
}
