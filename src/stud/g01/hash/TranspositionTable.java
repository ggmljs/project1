package stud.g01.hash;

import java.util.Arrays;

/**
 * 转置表（transposition table，成员四负责）。
 *
 * <p>IDA* 每一轮都是“限定 f ≤ 阈值”的深度优先搜索。同一轮里，
 * 同一个棋盘状态可能经不同的移动序列（即不同的父结点）被再次到达，
 * 这就是“移项 / transposition”。转置表按 Zobrist 哈希记录
 * “本轮已经以多大的 g 访问过该状态”，从而剪掉重复的子树：</p>
 *
 * <ul>
 *   <li>本轮以 g 访问过某状态时，它的整棵子树都已按当前阈值搜完；</li>
 *   <li>再次以 g' ≥ g 到达该状态时，子树内容完全相同，可以直接剪枝；</li>
 *   <li>以更小的 g' 到达时不能剪，因为更短的路径可能带来更好的解，
 *       这时更新表项并重新展开。</li>
 * </ul>
 *
 * <p>表项只在“当前一轮”内有效：阈值每次迭代都会变大，上一轮被阈值截断的
 * 子树在本轮必须重新探索，所以每轮开始时调用 {@link #beginIteration()}
 * 把代数加一，旧表项自动失效（惰性清除，不做 O(表大小) 的清空）。</p>
 *
 * <p>实现采用固定大小的开放定址表：每个桶 2 个槽，桶内线性比较；
 * 新表项优先放进空槽，桶满时替换掉 g 最大的槽（g 大的表项剪枝能力最弱）。</p>
 *
 * <p>表只保存 Zobrist 哈希、g 和代数，不保存完整棋盘：64 位哈希的碰撞概率
 * 约为 2^-64，可以认为相等即同一状态；此假设由
 * {@code ZobristHashSelfTest} 在 8 数码全部 181440 个可达状态上验证。</p>
 */
public final class TranspositionTable {

    /** 每个桶的槽位数 */
    private static final int BUCKET_SIZE = 2;

    /** 表大小的二进制位数，槽位数 = 2^tableBits */
    private final int tableBits;

    /** 桶数 - 1，用于把哈希值映射到桶 */
    private final int bucketMask;

    private final long[] keys;
    private final int[] costs;
    private final int[] stamps;

    /** 当前代数；stamp 与代数相同才视为本轮有效 */
    private int generation;

    private long stores;
    private long lookups;

    /**
     * 创建转置表。
     *
     * @param tableBits 槽位数的二进制位数，取值范围 [8, 26]；
     *                  tableBits = 22 时占 (8 + 4 + 4) 字节 × 2^22 ≈ 64 MiB
     * @throws IllegalArgumentException 位数越界时抛出
     */
    public TranspositionTable(int tableBits) {
        if (tableBits < 8 || tableBits > 26) {
            throw new IllegalArgumentException("tableBits 必须在 [8, 26] 之间：" + tableBits);
        }
        this.tableBits = tableBits;
        int slots = 1 << tableBits;
        this.keys = new long[slots];
        this.costs = new int[slots];
        this.stamps = new int[slots];
        this.bucketMask = slots / BUCKET_SIZE - 1;
    }

    /**
     * 开始新的一轮（一次 IDA* 迭代），使上一轮的表项全部失效。
     */
    public void beginIteration() {
        if (generation == Integer.MAX_VALUE) {
            clear();
        }
        generation++;
    }

    /**
     * 查询某状态本轮记录的最小 g。
     *
     * @param key Zobrist 哈希
     * @return 本轮记录过的 g；本轮没有记录时返回 {@link Integer#MAX_VALUE}
     */
    public int bestCost(long key) {
        lookups++;
        if (generation == 0) {
            // 还没有开始任何一轮：表里没有有效表项
            return Integer.MAX_VALUE;
        }
        int base = baseIndex(key);
        for (int i = 0; i < BUCKET_SIZE; i++) {
            int slot = base + i;
            if (stamps[slot] == generation && keys[slot] == key) {
                return costs[slot];
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 记录“状态 key 本轮以 g = cost 被访问过”。同一状态重复记录时保留更小的 g。
     *
     * @param key  Zobrist 哈希
     * @param cost 该状态当前的路径代价 g，必须非负
     * @throws IllegalArgumentException g 为负时抛出
     */
    public void store(long key, int cost) {
        if (generation == 0) {
            throw new IllegalStateException("写入表项前必须先调用 beginIteration()");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("g 不能为负：" + cost);
        }

        int base = baseIndex(key);
        int emptySlot = -1;
        int worstSlot = -1;
        int worstCost = Integer.MIN_VALUE;

        for (int i = 0; i < BUCKET_SIZE; i++) {
            int slot = base + i;
            if (stamps[slot] == generation && keys[slot] == key) {
                if (cost < costs[slot]) {
                    costs[slot] = cost;
                }
                stores++;
                return;
            }
            if (stamps[slot] != generation) {
                if (emptySlot < 0) {
                    emptySlot = slot;
                }
            } else if (costs[slot] > worstCost) {
                worstCost = costs[slot];
                worstSlot = slot;
            }
        }

        // 优先用空槽；桶满时替换掉 g 最大的槽
        int slot = emptySlot >= 0 ? emptySlot : worstSlot;
        if (slot < 0) {
            return;
        }
        keys[slot] = key;
        costs[slot] = cost;
        stamps[slot] = generation;
        stores++;
    }

    /**
     * 清空整张表（所有代的表项都失效）。
     */
    public void clear() {
        Arrays.fill(stamps, 0);
        generation = 0;
        stores = 0;
        lookups = 0;
    }

    /**
     * 哈希值到桶起点的映射。先把 key 充分混合（splitmix64 风格），
     * 避免低位规律导致桶分布不均。
     */
    private int baseIndex(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) (h & bucketMask) * BUCKET_SIZE;
    }

    /** @return 表大小的二进制位数 */
    public int tableBits() {
        return tableBits;
    }

    /** @return 槽位总数 */
    public int capacity() {
        return keys.length;
    }

    /** @return 当前代数 */
    public int generation() {
        return generation;
    }

    /** @return 累计写入次数（含同状态更新） */
    public long stores() {
        return stores;
    }

    /** @return 累计查询次数 */
    public long lookups() {
        return lookups;
    }

    @Override
    public String toString() {
        return "TranspositionTable(bits=" + tableBits + ", generation=" + generation
                + ", stores=" + stores + ", lookups=" + lookups + ")";
    }
}
