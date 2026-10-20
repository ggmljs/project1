package stud.g01.hash;

/**
 * TranspositionTable 的自测（不依赖 JUnit，直接运行 main）。
 *
 * <p>覆盖：空表查询、写入/读取、同状态保留更小的 g、换代后旧表项失效、
 * 清空、容量与参数校验。</p>
 */
public final class TranspositionTableSelfTest {

    private static int checks = 0;

    public static void main(String[] args) {
        testEmptyTable();
        testStoreAndLookup();
        testKeepSmallerCost();
        testGenerationInvalidatesOldEntries();
        testClear();
        testManyEntries();
        testIllegalArguments();

        System.out.println("TranspositionTableSelfTest: all tests passed. checks=" + checks);
    }

    private static void testEmptyTable() {
        TranspositionTable table = new TranspositionTable(8);
        table.beginIteration();
        expect(table.bestCost(12345L) == Integer.MAX_VALUE, "空表查询返回 MAX_VALUE");
        expect(table.capacity() == 256, "2^8 个槽位");
        expect(table.tableBits() == 8, "记录表大小");
        expect(table.generation() == 1, "换代计数从 1 开始");
    }

    private static void testStoreAndLookup() {
        TranspositionTable table = new TranspositionTable(10);
        table.beginIteration();
        table.store(42L, 7);
        expect(table.bestCost(42L) == 7, "写入后能读回 g");
        expect(table.bestCost(43L) == Integer.MAX_VALUE, "未写入的 key 查不到");
    }

    private static void testKeepSmallerCost() {
        TranspositionTable table = new TranspositionTable(10);
        table.beginIteration();
        table.store(42L, 9);
        table.store(42L, 5);
        expect(table.bestCost(42L) == 5, "同一状态记录更小的 g");
        table.store(42L, 8);
        expect(table.bestCost(42L) == 5, "再写入更大的 g 不会覆盖更小的 g");
    }

    private static void testGenerationInvalidatesOldEntries() {
        TranspositionTable table = new TranspositionTable(10);
        table.beginIteration();
        table.store(42L, 3);
        expect(table.bestCost(42L) == 3, "本轮写入的表项本轮可见");

        table.beginIteration();
        expect(table.bestCost(42L) == Integer.MAX_VALUE, "换代后上一轮的表项失效");

        table.store(42L, 4);
        expect(table.bestCost(42L) == 4, "新一轮重新记录后可见");
        expect(table.generation() == 2, "换代计数递增");
    }

    private static void testClear() {
        TranspositionTable table = new TranspositionTable(8);
        table.beginIteration();
        table.store(1L, 1);
        table.clear();
        expect(table.stores() == 0 && table.lookups() == 0, "clear 之后统计归零");
        expect(table.bestCost(1L) == Integer.MAX_VALUE, "clear 之后查不到旧表项");
        expect(table.generation() == 0, "clear 之后代数归零");
    }

    private static void testManyEntries() {
        TranspositionTable table = new TranspositionTable(16);
        table.beginIteration();

        int count = 1000;
        for (int i = 0; i < count; i++) {
            // 乘上奇数把 key 打散，模拟 Zobrist 值的分布
            table.store(i * 0x9E3779B97F4A7C15L, i % 32);
        }

        int readable = 0;
        for (int i = 0; i < count; i++) {
            int cost = table.bestCost(i * 0x9E3779B97F4A7C15L);
            if (cost == i % 32) {
                readable++;
            }
        }
        expect(readable == count, "4 万个槽位放 1000 个表项后全部可读，实际可读 " + readable);
        expect(table.stores() >= count, "写入次数被统计");
        expect(table.lookups() >= count, "查询次数被统计");
    }

    private static void testIllegalArguments() {
        expectThrows(() -> new TranspositionTable(7), "表大小过小");
        expectThrows(() -> new TranspositionTable(27), "表大小过大");

        TranspositionTable table = new TranspositionTable(8);
        table.beginIteration();
        expectThrows(() -> table.store(1L, -1), "g 为负应报错");
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
