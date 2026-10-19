package stud.g01.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 基于优先队列的 Frontier，即搜索算法中的 Open 表（成员三负责）。
 *
 * <p>框架说明（见 core.solver.queue.Frontier）：插入的结点如果与 Frontier 中已有结点
 * 状态相同，应当舍弃二者之中“不好”的那一个。本类的实现策略是：</p>
 *
 * <ul>
 *   <li>用 {@link HashMap} 建立“状态 → Frontier 中对应结点”的索引，
 *       使“某状态是否已在 Open 表中”的判定从遍历（O(n)）降为 O(1)；</li>
 *   <li>插入时若索引中已有同状态结点，则用比较器判断新结点是否更优：
 *       更优则摘掉旧结点、插入新结点，否则直接丢弃新结点；</li>
 *   <li>“更优”的判定直接复用 {@link PriorityQueue#comparator()}，
 *       因此本类同时支持框架的三种估值机制（{@code EvaluationType}）：
 *       FULL（f = g + h，相同时取 g 小的）、PATH_COST（g）、HEURISTIC（h），
 *       与 Frontier 内部的排序规则严格一致，不会出现“排序用一个标准、
 *       去重却用另一个标准”的不一致。</li>
 * </ul>
 *
 * <p>注意：poll() 会把结点从索引中移除；clear() 会同时清空索引。
 * 请不要用迭代器直接删除元素（会绕过索引），需要删除请使用 remove(Object)。</p>
 */
public class PqFrontier extends PriorityQueue<Node> implements Frontier {

    /**
     * 状态 -> Frontier 中该状态对应的结点。
     * 状态相等以 {@link Node#equals(Object)}（即 State.equals）为准。
     */
    private final Map<State, Node> stateIndex = new HashMap<>();

    /**
     * 使用 Node 的自然序（f = g + h）排序的 Frontier。
     */
    public PqFrontier() {
        super();
    }

    /**
     * 使用指定比较器排序的 Frontier，比较器一般来自 Node.evaluator(type)，
     * 即框架按 EvaluationType 提供的三种估值机制。
     *
     * @param comparator 结点比较器，不允许为 null
     */
    public PqFrontier(Comparator<Node> comparator) {
        super(requireComparator(comparator));
    }

    private static Comparator<Node> requireComparator(Comparator<Node> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("comparator cannot be null");
        }
        return comparator;
    }

    /**
     * Frontier 中是否已经有与 node 状态相同的结点。
     */
    @Override
    public boolean contains(Node node) {
        if (node == null) {
            return false;
        }
        return stateIndex.containsKey(node.getState());
    }

    /**
     * 插入结点：同状态只保留估值更好的那个。
     *
     * @param node 要插入的结点
     * @return 结点确实进入了 Frontier 时返回 true；
     *         因为已存在更好的同状态结点而被丢弃时返回 false
     */
    @Override
    public boolean offer(Node node) {
        if (node == null) {
            throw new NullPointerException("node cannot be null");
        }

        Node incumbent = stateIndex.get(node.getState());
        if (incumbent == null) {
            return insert(node);
        }

        if (isBetter(node, incumbent)) {
            // 用更好的结点替换旧结点：PriorityQueue 不支持改键，
            // 只能先按 state 相等（Node.equals）把旧结点摘掉
            super.remove(incumbent);
            return insert(node);
        }

        // 旧结点不差于新结点，丢弃新结点
        return false;
    }

    /**
     * 取出估值最小的结点，并同步维护状态索引。
     */
    @Override
    public Node poll() {
        Node node = super.poll();
        if (node != null) {
            stateIndex.remove(node.getState());
        }
        return node;
    }

    @Override
    public void clear() {
        super.clear();
        stateIndex.clear();
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);
        if (removed && o instanceof Node) {
            stateIndex.remove(((Node) o).getState());
        }
        return removed;
    }

    /**
     * 真正把结点放进优先队列，并登记到状态索引。
     */
    private boolean insert(Node node) {
        boolean offered = super.offer(node);
        if (offered) {
            stateIndex.put(node.getState(), node);
        }
        return offered;
    }

    /**
     * candidate 是否比 incumbent 更值得保留。
     *
     * <p>比较器已经包含“f 相同则取 g 小的”这类次级规则（见 Node.evaluator），
     * 因此这里只需比较结果是否小于 0。</p>
     */
    private boolean isBetter(Node candidate, Node incumbent) {
        Comparator<? super Node> comparator = comparator();
        int order = (comparator == null)
                ? candidate.compareTo(incumbent)
                : comparator.compare(candidate, incumbent);
        return order < 0;
    }

    /**
     * 便于测试与调试：返回当前索引中的结点个数，正常情况下应等于 size()。
     */
    public int indexedSize() {
        return stateIndex.size();
    }
}
