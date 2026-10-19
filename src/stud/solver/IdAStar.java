package stud.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * 临时接线类（组内需确认，成员三为让整个项目能编译而添加）。
 *
 * <p>框架文件 core/runner/EngineFeeder.java 第 11 行写的是
 * {@code import stud.solver.IdAStar;}，并且第 54 行调用
 * {@code new IdAStar(frontier, predictor)}，但框架源码里并没有 stud.solver 包下的
 * IdAStar 源文件（只有一个用更高版本 Java 编译的 .class，JDK 17 无法加载）。
 * 按课程要求“框架已有代码不要修改”，这里补一个同包同名的适配类，
 * 使 EngineFeeder 无需改动即可编译。</p>
 *
 * <p>第一阶段（A* + 优先队列）不会调用 {@link #search(Problem)}，
 * 因此本类目前只是占位实现。成员四完成 IDA*（阈值迭代 + Zobrist 去重）后，
 * 两种处理方式二选一，全组统一即可：</p>
 *
 * <ol>
 *   <li>把 stud.g01.solver.IdAStar 的正式实现搬到这里（并保持
 *       {@code (Frontier, Predictor)} 构造函数），本类即为正式 IDA*；</li>
 *   <li>或者把 EngineFeeder 的 import 改成 stud.g01.solver.IdAStar，
 *       然后删除本类（这属于修改框架文件，需要全组确认）。</li>
 * </ol>
 */
public final class IdAStar extends AbstractSearcher {

    /** 启发函数，用于 IDA* 的阈值 f = g + h */
    private final Predictor predictor;

    /**
     * 框架 EngineFeeder.getIdaStar() 使用的构造函数。
     *
     * @param frontier  栈式 Frontier（配合迭代加深，由框架创建）
     * @param predictor 启发函数
     */
    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        if (predictor == null) {
            throw new IllegalArgumentException("predictor cannot be null");
        }
        this.predictor = predictor;
    }

    /**
     * 供后续实现使用：取出本次搜索使用的启发函数。
     */
    public Predictor predictor() {
        return predictor;
    }

    /**
     * 尚未实现（阶段二任务，成员四负责）。
     */
    @Override
    public Deque<Node> search(Problem problem) {
        throw new UnsupportedOperationException(
                "IDA*（阶段二）由成员四实现：请在 stud.solver.IdAStar 中实现阈值迭代，"
                        + "或把 stud.g01.solver.IdAStar 的正式实现迁到本类");
    }
}
