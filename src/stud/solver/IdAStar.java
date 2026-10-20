package stud.solver;

import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Frontier;
import stud.g01.solver.IdAStar.Options;

/**
 * IDA* 的框架接线类（成员三创建，成员四在第二阶段升级为正式实现）。
 *
 * <p>框架文件 {@code core/runner/EngineFeeder.java} 的 import 固定为
 * {@code stud.solver.IdAStar}，并调用 {@code new IdAStar(frontier, predictor)}，
 * 课程要求“框架已有代码不要修改”，因此正式的 IDA* 算法放在成员四负责的
 * {@link stud.g01.solver.IdAStar}，本类只做两件事：</p>
 *
 * <ol>
 *   <li>继承成员四的完整实现，保持框架要求的 {@code (Frontier, Predictor)} 构造函数；</li>
 *   <li>把带参数的 Options 构造函数一并暴露出来，方便消融实验。</li>
 * </ol>
 *
 * <p>这样 {@code EngineFeeder.getIdaStar(type)} 不需要任何改动就能拿到真正的
 * IDA*（阈值迭代 + 深度优先 + Zobrist 路径去环 + 转置表）。</p>
 */
public final class IdAStar extends stud.g01.solver.IdAStar {

    /**
     * 框架 {@code EngineFeeder.getIdaStar()} 使用的构造函数。
     *
     * @param frontier  框架传入的栈式 Frontier
     * @param predictor 启发函数
     */
    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier, predictor);
    }

    /**
     * 带搜索参数的构造函数，供 benchmark 做去重消融实验。
     *
     * @param frontier  栈式 Frontier
     * @param predictor 启发函数
     * @param options   搜索参数
     */
    public IdAStar(Frontier frontier, Predictor predictor, Options options) {
        super(frontier, predictor, options);
    }
}
