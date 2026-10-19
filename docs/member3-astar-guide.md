# 成员三：A\*、优先队列与基础启发式交接说明

## 1. 本模块包含的文件

正式代码：

- `src/stud/g01/queue/PqFrontier.java`（优先队列 Frontier / Open 表）
- `src/stud/g01/heuristic/MisplacedTilesPredictor.java`（错位将牌数）
- `src/stud/g01/heuristic/ManhattanPredictor.java`（曼哈顿距离）
- `src/stud/g01/runner/PuzzleFeeder.java`（实例解析 + Frontier/启发函数工厂）
- `src/stud/solver/IdAStar.java`（**接线类**，见第 8.1 节，需组内确认）

自测与实验工具：

- `test/stud/g01/queue/PqFrontierSelfTest.java`
- `test/stud/g01/heuristic/PredictorSelfTest.java`
- `test/stud/g01/runner/PuzzleFeederSelfTest.java`
- `test/stud/g01/runner/AStarStageOneBenchmark.java`（阶段一性能数据生成工具）

新增数据文件：

- `resources/problems8.txt`（只含 3 个 8 数码实例，用于阶段一验收，见 6.5）

本模块负责**第一阶段**：A\*（框架 `BestFirstSearcher`）+ 优先队列 Frontier +
两个基础启发函数，交付"第一阶段可运行版本"和"A\* 性能数据"。

框架的搜索算法本身不需要写，本模块只负责三件事：给出 Open 表的数据结构、
给出启发函数、把输入文本变成问题实例。

## 2. 框架调用关系

```text
core.runner.SearchTester
        │  feeder.getProblems(lines)      → ArrayList<Problem>
        │  feeder.getAStar(type)          → BestFirstSearcher(frontier, predictor)
        ▼
EngineFeeder.getAStar / getDijkstra / getIdaStar
        │  getFrontier(EvaluationType)    → PqFrontier（带比较器）
        │  getPredictor(HeuristicType)    → MisplacedTiles / Manhattan
        ▼
BestFirstSearcher.search(problem)
        │  problem.root(predictor)        → 根结点（含 h 值）
        │  frontier.offer / frontier.poll → Open 表
        │  explored.add / expanded        → Closed 表（框架 AbstractSearcher 提供）
        │  problem.childNodes(node, predictor)
        ▼
PuzzleBoard.next(action)  ← 成员一；NPuzzleProblem.applicable/stepCost/showSolution ← 成员二
```

## 3. PqFrontier（Open 表）

### 3.1 为什么不是"直接 extends PriorityQueue 就完事"

`Frontier` 接口对 `offer` 的要求是：**同一个状态只保留更好的结点**。
朴素做法是每次插入都遍历整个优先队列查找同状态结点，复杂度 O(n)，
而且 A\* 里 `offer` 的调用次数与生成的结点数成正比（本例中多达 38 万个），
遍历会成为主要开销。

本实现额外维护一张索引：

```java
Map<State, Node> stateIndex;
```

- `contains(node)`：查索引，O(1)；
- `offer(node)`：O(1) 判断是否已有同状态结点，只有"确实要替换"时才做一次
  `PriorityQueue.remove`（O(n)），其余情况直接丢弃或直接插入。

于是"重复状态判定"从 O(n) 降到 O(1)，这是本模块对数据结构的主要改进，
报告里可以作为"Frontier 数据结构改进"的论据。

### 3.2 三种估值机制

`PqFrontier(Comparator<Node> comparator)` 的比较器来自 `Node.evaluator(type)`，
因此同一份实现支持框架的全部三种估值机制：

| EvaluationType | 比较器含义 | 对应算法 |
| --- | --- | --- |
| FULL | f = g + h，f 相同取 g 小 | A\* |
| PATH_COST | 按 g 升序 | Dijkstra |
| HEURISTIC | 按 h 升序 | 贪心最佳优先 |

"谁更好"直接用**同一个比较器**判断（`comparator.compare(新, 旧) < 0`），
不会出现"排序用一个标准、去重却用另一个标准"的不一致。

### 3.3 状态相等的语义

`Node.equals` 比较的是 `state.equals(other.state)`，`PuzzleBoard` 已按棋盘内容实现
`equals/hashCode`（成员一），所以：

- `hashCode` 相同 ⇔ 棋盘内容相同；
- 索引 `Map<State, Node>` 的去重语义与框架 `explored`（`HashSet<State>`）一致。

### 3.4 已知代价与可选优化

- 替换结点时 `PriorityQueue.remove` 仍是 O(n)，只有"新结点严格更优"时才会触发；
  A\* 在启发函数满足单调性时几乎不触发，影响很小。
- 如果需要进一步优化，可以改用"允许重复插入 + 出队时判 expanded"（框架
  `BestFirstSearcher` 的注释里提到的第二种策略），代价是内存与重复扩展增加。
- 不要用迭代器直接删除元素（会绕过 `stateIndex`），需要删除请用 `remove(Object)`。

## 4. 两个基础启发函数

两者都实现 `core.solver.algorithm.heuristic.Predictor`，都**跳过空格 0**，
都通过 `board.getTile(index)`、`board.getSize()` 访问棋盘（不调用 `getTiles()`，
因为每次调用都会复制数组）。

### 4.1 错位将牌数 `MisplacedTilesPredictor`

统计不在目标位置上的将牌个数。可采纳，但很松弛：
目标状态返回 0，每步最多让一个将牌归位。

### 4.2 曼哈顿距离 `ManhattanPredictor`

对每块将牌计算 `|行差| + |列差|` 再求和，同样可采纳，且恒有
曼哈顿距离 ≥ 错位将牌数。

性能要点：把"数字 → 目标行列"预计算成 `int[] goalRow / goalCol` 并缓存。
框架在每次扩展时都会用**同一个 goal 对象**调用启发函数，
因此用引用比较（`goalBoard == cachedGoal`）就能判断缓存是否可用，
一次搜索只建一次表；如果调用者传入了新的（内容相同的）goal 对象，
表会重建，结果依然正确。

### 4.3 手工验算过的测试值

`resources/problems.txt` 第一个实例：

```text
5 _ 8
4 2 1
7 3 6        目标：1 2 3 / 4 5 6 / 7 8 _
```

- 错位将牌数 = 6（5、8、2、1、3、6 错位）
- 曼哈顿距离 = 13

两个值都写进了 `PredictorSelfTest`，换实现时可以直接回归。

## 5. PuzzleFeeder

### 5.1 输入格式

`resources/problems.txt` 每行一个实例：

```text
边长  初始布局的 n*n 个数  目标布局的 n*n 个数
```

3 阶共 19 个字段，4 阶共 33 个字段，数字按行展开、0 表示空格。例如：

```text
3 5 0 8 4 2 1 7 3 6 1 2 3 4 5 6 7 8 0
```

解析规则：

- 空行、以 `#` 开头的行跳过；
- 字段个数、边长、非整数、重复数字都会抛出带**行号**的 `IllegalArgumentException`；
- 校验工作复用 `PuzzleBoard` 构造函数（成员一），本模块不重复实现。

### 5.2 工厂方法

```java
getFrontier(EvaluationType.FULL)        → new PqFrontier(Node.evaluator(FULL))
getFrontier(EvaluationType.PATH_COST)  → 同上（Dijkstra）
getFrontier(EvaluationType.HEURISTIC)  → 同上（贪心）

getPredictor(MISPLACED)                → new MisplacedTilesPredictor()
getPredictor(MANHATTAN)                → new ManhattanPredictor()
getPredictor(DISJOINT_PATTERN)         → 抛出 UnsupportedOperationException（成员五接入点）
```

### 5.3 成员五的接入点

`PuzzleFeeder.getPredictor` 的 `DISJOINT_PATTERN` 分支已经留好，并写了说明：
成员五只需把该分支改成返回自己的模式数据库预测器实例即可，其余代码不用动。
（模式数据库需要按问题实例建表，所以它的构造函数很可能需要 `Problem` 列表——
届时可以给 `PuzzleFeeder` 加一个字段，在 `getProblems()` 里保存解析结果。）

## 6. 编译、运行与自测

### 6.1 编码注意（重要）

框架 `.java` 是 **GBK**，成员一/二/三新增的文件是 **UTF-8**。
命令行一次性编译会报"不可映射字符"，这不是代码错误：

```text
javac -encoding GBK ...   # 成员的文件会被读错
javac -encoding UTF-8 ... # 框架的文件会被读错
```

两种处理方式：

- IDEA：`File → Settings → Editor → File Encodings` 建议整体设为 UTF-8，
  IDEA 会按文件实际编码打开（乱码不影响编译）；或对 `src/core`、`src/stud/problem`、
  `src/stud/queue`、`src/stud/runner`、`src/algs4` 这些框架目录单独设 GBK。
- 命令行：先把 GBK 文件转成 UTF-8（或反过来），再统一用 `-encoding UTF-8` 编译。
  本成员验证时是在临时副本里统一转成 UTF-8 后编译运行的。

### 6.2 第一阶段运行

推荐用只含 8 数码实例的实例文件（本模块新增，见 6.5）：

```text
java -cp out core.runner.SearchTester resources/problems8.txt NPUZZLE 1 stud.g01.runner.PuzzleFeeder
```

`SearchTester` 在阶段 1 会自动用 `MISPLACED` 和 `MANHATTAN` 各跑一遍，
输出每个实例的解路径、解长度、耗时、生成的结点数、扩展的结点数。

`resources/problems.txt` 里第 4 行是 4 阶（15-Puzzle）实例，阶段一不要求解它。
**不要直接把它喂给阶段一**：实测在 `-Xmx1g` 下用 MISPLACED 解它直接抛
`OutOfMemoryError`（在 8 数码实例上不会出现）。阶段一的验收请只看 8 数码实例。

### 6.3 自测（不依赖 JUnit，直接运行 main）

```text
java -cp out stud.g01.queue.PqFrontierSelfTest
java -cp out stud.g01.heuristic.PredictorSelfTest
java -cp out stud.g01.runner.PuzzleFeederSelfTest
```

预期输出：

```text
PqFrontierSelfTest: all tests passed. checks=27
PredictorSelfTest: all tests passed. checks=618
PuzzleFeederSelfTest: all tests passed. checks=31
```

覆盖内容：

- `PqFrontier`：三种估值机制的出队顺序、同状态替换/丢弃、contains 的状态语义、
  clear 与索引一致性；
- `Predictor`：目标状态为 0、空格不参与统计、两个手工算例的精确值、
  300 个随机布局上"曼哈顿 ≥ 错位"、目标表缓存命中与重建、非法入参；
- `PuzzleFeeder`：单行解析、空行/注释跳过、格式错误、工厂方法、
  不可解实例直接返回 null、端到端 A\*（解长 21、逐步回放到达目标、结点统计）。

### 6.4 性能数据工具

```text
# resources/problems.txt 里的 8 数码实例（4 阶自动跳过）
java -cp out stud.g01.runner.AStarStageOneBenchmark

# 随机生成 20 个可解实例（固定种子，可复现）
java -cp out stud.g01.runner.AStarStageOneBenchmark --random 20
```

输出是 Markdown 表格，每行都校验解路径是否真的到达目标，可以直接粘进报告。

### 6.5 新增的实例文件

`resources/problems8.txt`：只保留 `problems.txt` 里的 3 个 8 数码实例，
用于阶段一验收（避免 4 阶实例把时间/内存耗光）。内容与 `problems.txt` 前 3 行完全一致，
`problems.txt` 本身没有改动。

## 7. 第一阶段实验数据

### 7.1 课程给定实例（3 个 8 数码）

> 说明：生成/扩展结点数是确定值（每次运行完全一致）；耗时是单次测量的墙钟时间，
> 不同机器、不同 JVM 预热状态下会有 10% 左右的波动，正式报告建议多跑几次取中位数。

| 实例 | 启发函数 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 解校验 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| #1 | MISPLACED | 6 | 21 | 12.080 | 15677 | 5794 | 通过 |
| #2 | MISPLACED | 7 | 31 | 159.094 | 385076 | 143848 | 通过 |
| #3 | MISPLACED | 7 | 31 | 120.998 | 385076 | 143848 | 通过 |
| #1 | MANHATTAN | 13 | 21 | 1.703 | 2489 | 931 | 通过 |
| #2 | MANHATTAN | 21 | 31 | 17.226 | 55925 | 21197 | 通过 |
| #3 | MANHATTAN | 21 | 31 | 11.924 | 55925 | 21197 | 通过 |

| 启发函数 | 实例数 | 求解成功 | 平均用时(ms) | 平均生成结点 | 平均扩展结点 |
| --- | ---: | ---: | ---: | ---: | ---: |
| MISPLACED | 3 | 3 | 97.391 | 261943 | 97830 |
| MANHATTAN | 3 | 3 | 10.284 | 38113 | 14441 |

### 7.2 随机实例（20 个可解 8 数码，种子 20240301）

| 启发函数 | 实例数 | 求解成功 | 平均用时(ms) | 平均生成结点 | 平均扩展结点 |
| --- | ---: | ---: | ---: | ---: | ---: |
| MISPLACED | 20 | 20 | 10.394 | 38376 | 14209 |
| MANHATTAN | 20 | 20 | 1.130 | 4492 | 1685 |

### 7.3 结论（报告第一阶段可直接用）

1. **两个启发函数都满足 1 秒限制**。最坏情况是给定实例 #2 用 MISPLACED，
   耗时 159 ms；换成 MANHATTAN 后降到 17 ms。
2. **曼哈顿距离显著优于错位将牌数**：随机 20 例中，平均生成结点从 38376
   降到 4492（减少 88.3%），平均耗时从 10.39 ms 降到 1.13 ms（约 9.2 倍）。
3. **解长度两者完全一致**（同一实例都是最优解，如 21、31 步），说明两个启发函数
   都可采纳，A\* 的最优性没有被牺牲；差别只在搜索规模。
4. **MISPLACED 在 31 步这类难实例上接近"扫遍状态空间"**：
   扩展了 143848 个结点，而 8 数码全部可达状态只有 181440 个。
   这也解释了为什么难实例上它的耗时接近百毫秒，而 MANHATTAN 只要十几毫秒。
5. 给定实例 #2 与 #3 在两个启发函数下的生成/扩展结点数**完全相同**
   （用 `SearchTester` 和本文工具各复现一次），它们的解长也都是 31 步；
   报告里可以把这对实例作为"结点数与启发值剖面一致"的观察点，进一步讨论。

## 8. 与其他成员的接口约定

### 8.1 框架编译错误：`stud.solver.IdAStar`（需组内确认）

框架 `core/runner/EngineFeeder.java` 有两处问题：

- 第 11 行 `import stud.solver.IdAStar;`——该包不存在；
- 第 54 行 `new IdAStar(frontier, predictor)`——需要 `(Frontier, Predictor)` 构造函数。

框架自带的 `out/production/WattAi/stud/solver/IdAStar.class` 是 Java 19 字节码
（major 63），本机 JDK 17 直接报"文件版本 63.0，应为 61.0"，无法使用。

按"框架已有代码不要修改"的要求，本模块新增了 `src/stud/solver/IdAStar.java`
作为**临时接线类**：同包同名、`(Frontier, Predictor)` 构造函数、继承
`AbstractSearcher`，`search()` 抛 `UnsupportedOperationException`。
第一阶段完全不需要 IDA\*，所以不影响本模块；它只是让整个项目能编译。

成员四开始做第二阶段时，二选一，**全组统一**：

1. 把 IDA\* 的正式实现放到 `stud.solver.IdAStar`（保持两参构造函数），这个类就地变成正式实现；
2. 或者把 `EngineFeeder` 的 import 改成 `stud.g01.solver.IdAStar` 并删除本类
   ——这条改动了框架文件，需要全组同意。

### 8.2 成员四（IDA\*）

- `EngineFeeder.getIdaStar()` 固定的构造签名是 `(Frontier, Predictor)`，
  传入的 Frontier 是 `StackFrontier`；
- IDA\* 用到的启发函数直接复用本模块的两个 `Predictor`；
- Zobrist 去重不要修改 `PuzzleBoard.equals()`（成员一文档第 8 节）。

### 8.3 成员五（模式数据库与可视化）

- 接入点：`PuzzleFeeder.getPredictor(HeuristicType.DISJOINT_PATTERN)`；
- `SearchTester` 在 `step == 3` 时只会请求 `DISJOINT_PATTERN`，
  所以第三阶段只需要这一个分支对接；
- 可视化需要的"解路径文本"来自 `NPuzzleProblem.showSolution`，
  本模块没有改动它的输出格式。

### 8.4 与成员一/二的约定

- 只用 `PuzzleBoard.getTile(index/getRow,Col)`、`getSize()`、`getBlankIndex()`，
  不调用 `getTiles()`（会复制数组）；
- 不重复实现移动边界判断，一律走 `NPuzzleProblem.applicable()`（框架 `childNodes` 会调）；
- 动作方向是**空格的移动方向**（与成员一一致）。

## 9. 职责边界

本模块已完成：

- `PqFrontier`（三种估值机制、状态索引去重）；
- 错位将牌数、曼哈顿距离两个 `Predictor`；
- `PuzzleFeeder`（实例解析、Frontier/启发函数工厂、DISJOINT_PATTERN 接入点）；
- 第一阶段 A\* 可运行版本 + 性能数据 + 三个自测 + 数据生成工具。

本模块**没有**实现（属于其他成员）：

- `PuzzleBoard` / `PuzzleMove`（成员一）；
- `NPuzzleProblem`（成员二）；
- IDA\*、Zobrist Hash（成员四）；
- Disjoint Pattern Database、解路径可视化（成员五）；
- 4 阶（15-Puzzle）的性能数据——阶段一不要求，留给阶段二/三。

## 10. 已知问题与待办

1. **`stud.solver.IdAStar` 需要组内确认**（见 8.1），目前是占位实现。
2. `problems.txt` 第 4 行是 4 阶实例，直接跑 `SearchTester` 阶段一会把时间花在它身上，
   实测在 `-Xmx1g` 下用 MISPLACED 解它会 `OutOfMemoryError`；验收"1 秒"请只看 8 数码实例
   （用 `resources/problems8.txt`，或 `AStarStageOneBenchmark`——它自动跳过 4 阶）。
3. 源码编码混杂（GBK + UTF-8），命令行编译要先统一编码，否则报"不可映射字符"。
4. `PqFrontier` 替换结点时 `remove` 是 O(n)；如果阶段三要跑 4 阶，
   建议先按 7.3 第 4 条的经验评估，必要时换成"允许重复插入"的策略做对比实验。
5. 本模块的耗时数据是单次测量（JVM 已预热一次），正式报告建议多跑几次取中位数。
