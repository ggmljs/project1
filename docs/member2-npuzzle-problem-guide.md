# 成员二：NPuzzleProblem 问题模型交接说明

## 1. 本模块包含的文件

正式代码：

- `src/stud/g01/problem/npuzzle/NPuzzleProblem.java`（本次完成）

自测代码：

- `test/stud/g01/problem/npuzzle/NPuzzleProblemSelfTest.java`（本次完成）

本模块负责 N-Puzzle 的问题层：可解性判断、动作可用性判断、单步耗散和解路径显示。
棋盘状态层（`PuzzleBoard`、`PuzzleMove`）由成员一实现，本模块直接复用，未做任何修改。

## 2. 对外接口一览

### 2.1 solvable()

```java
public boolean solvable()
```

- 判断当前问题实例（`initialState` → `goal`）是否有解；
- 无解的实例在搜索前就会被拦截：`BestFirstSearcher.search()` 第一步就调用它，
  返回 false 时直接返回 null，不会浪费搜索时间；
- 判定算法：逆序数 + 空格行号的奇偶关系（见第 3 节）。

### 2.2 applicable(State state, Action action)

```java
public boolean applicable(State state, Action action)
```

- 状态不是 `PuzzleBoard`、动作不是 `PuzzleMove`、动作为 null 时一律返回 false；
- 其余情况委托给 `PuzzleBoard.canMove()`，本模块没有重复实现边界判断；
- 成员三在启发函数中**不需要**调用它，它只被框架的 `Problem.childNodes()` 使用。

### 2.3 stepCost(State state, Action action)

```java
public int stepCost(State state, Action action)
```

- N-Puzzle 每次合法移动代价恒为 1，直接返回 `action.stepCost()`；
- 动作类型不对时抛 `IllegalArgumentException`；
- 注意框架的调用约定：`childNodes()` 里传入的 `state` 是**后继状态**，
  但因为代价恒为 1，与传哪个状态无关，成员三无需关心这个细节。

### 2.4 showSolution(Deque&lt;Node&gt; path)

```java
public void showSolution(Deque<Node> path)
```

- 输出初始状态，然后逐步打印每一步的动作（UP/DOWN/LEFT/RIGHT，表示空格移动方向）
  和移动后的棋盘，最后打印总步数；
- **重要**：框架 `generatePath()` 返回的 path **不包含根结点**，
  按第一步 → 目标步的顺序排列，`path.size()` 就是解的步数；
- 输出同时包含动作序列和状态序列，后续可视化（成员五）可以直接解析
  这个文本，或仿照此方法重新遍历 path。

## 3. solvable() 的判定原理（报告可直接引用）

把棋盘按行展开、去掉空格 0，并把每个数字替换成它在**目标布局**中的名次，
统计该序列的逆序数 inv。记 n 为棋盘边长：

- **n 为奇数**：空格左右移动不改变逆序数（空格不在统计序列里），
  上下移动一次恰好跨过 n-1 个偶数个数字，逆序数奇偶性不变；
  当且仅当 inv 为偶数时有解。
- **n 为偶数**：上下移动一次逆序数奇偶性翻转，但空格所在行
  （从底部数，最底行为第 1 行）也同时变化 1；
  因此 "inv + 空格从底行号" 的奇偶性在全程保持不变；
  当且仅当初始状态与目标状态的该奇偶值相同时有解。

标准目标布局（1..n²-1 顺序排列、空格在右下角）的 inv 为 0，
所以退化为教科书结论：奇数阶逆序数为偶数即可解；
偶数阶 "逆序数 + 从底数起的空格行号" 为奇数即可解。

本实现的目标状态是从输入读入的任意布局，因此按一般形式实现：
逆序数是**相对于目标布局**计算的（目标布局自身的逆序数恒为 0）。

## 4. 自测方法

测试入口（不依赖 JUnit，直接运行 main()）：

```text
test/stud/g01/problem/npuzzle/NPuzzleProblemSelfTest.java
```

预期输出：

```text
NPuzzleProblemSelfTest: all tests passed.
```

测试覆盖：

- 3 阶可解/不可解实例、4 阶可解/不可解实例（含经典的 14-15 互换无解例）；
- **交叉验证**：BFS 从目标状态出发枚举 3 阶棋盘全部 181440 个可达状态，
  随机抽 500 个排列（固定种子，可复现），solvable() 判断与真实可达性完全一致；
- applicable 与 PuzzleBoard.canMove 在 200 个随机棋盘上一致；
- 错误类型（非 PuzzleMove 动作、非 PuzzleBoard 状态、null）返回 false；
- 每步代价为 1；
- goal() 判断；
- showSolution 对手工构造的解路径正常输出。

## 5. 成员三接入 A* 需要知道的事

### 5.1 你要实现的文件

- `src/stud/g01/queue/PqFrontier.java`：优先队列 Frontier；
- `src/stud/g01/runner/PuzzleFeeder.java`：Feeder；
- 启发函数（错位将牌数、曼哈顿距离），实现 `core.solver.algorithm.heuristic.Predictor`
  接口，注意**不要把空格 0 算进去**（成员一交接文档第 7 节有示例代码）。

### 5.2 PuzzleFeeder.getProblems 的输入格式

`resources/problems.txt` 每行一个实例：

```text
3 5 0 8 4 2 1 7 3 6 1 2 3 4 5 6 7 8 0
```

即：`边长 初始布局n²个数 目标布局n²个数`，均为按行展开。
构造 NPuzzleProblem 的方式（边长 size）：

```java
PuzzleBoard initial = new PuzzleBoard(size, 初始数组);
PuzzleBoard goal    = new PuzzleBoard(size, 目标数组);
NPuzzleProblem problem = new NPuzzleProblem(initial, goal, size);
```

构造完成后可以先调用 `problem.solvable()` 过滤无解实例，
也可以不过滤——搜索引擎自己会调。

### 5.3 SearchTester 的调用参数

```text
args[0] = resources/problems.txt
args[1] = NPUZZLE        （ProblemType 里的枚举名，以 ProblemType.java 实际定义为准）
args[2] = 1              （第一阶段）
args[3] = stud.g01.runner.PuzzleFeeder
```

第一阶段会自动使用 MISPLACED 和 MANHATTAN 两个启发函数各跑一遍
（见 `SearchTester.getHeuristicTypes()`）。

### 5.4 解路径与 showSolution 的衔接

搜索返回的 path 直接传给 `problem.showSolution(path)` 即可，
不需要再处理根结点。`path.size()` 就是解路径长度，
`SearchTester` 会打印这个值。

## 6. 职责边界

成员二已经完成：

- `NPuzzleProblem`（solvable / applicable / stepCost / showSolution）；
- 问题层自测。

本模块没有实现：

- `PqFrontier`、`PuzzleFeeder`（成员三）；
- 错位将牌数、曼哈顿距离启发函数（成员三）；
- A* 搜索引擎（框架的 `BestFirstSearcher` 已经提供，配置 Frontier + Predictor 即可）；
- IDA*、Zobrist Hash（成员四）；
- Disjoint Pattern Database、可视化（成员五）。

## 7. 当前已知的项目级问题（重要）

1. **`core/runner/EngineFeeder.java` 有一个框架自带的编译错误**：
   第 11 行 `import stud.solver.IdAStar;`，但实际类在 `stud.g01.solver.IdAStar`。
   这导致 `EngineFeeder`、`SearchTester`、两个 Feeder 都无法编译，
   也就是成员一交接文档第 11 节说的"最小接线修复"。
   修法有两种：把 import 改成 `stud.g01.solver.IdAStar`；
   或者把成员四的 IdAStar 类放进 `stud.solver` 包。两种都涉及
   "不改框架"的约定，需要全组确认后统一修，不要各修各的。
2. **成员四注意：IdAStar 的构造函数签名是框架定死的**。
   `EngineFeeder.getIdaStar()` 里调用 `new IdAStar(frontier, predictor)`，
   且传入的 Frontier 是 `StackFrontier`（栈式 Frontier，配合迭代加深），
   所以成员四的 IdAStar 必须提供 `(Frontier, Predictor)` 构造函数并继承
   `AbstractSearcher`，否则接线时编译不过。
3. **项目源码编码混杂**：老师框架的 .java 是 GBK 编码，成员一/二的文件是 UTF-8。
   在 IDEA 中建议把项目编码统一设为 UTF-8（File → Settings → Editor →
   File Encodings），对 GBK 文件会提示乱码但不影响编译
   （IDEA 默认按文件实际编码处理）。命令行编译时需要分开处理编码。
4. 本模块不依赖 JUnit，两个自测类都可以直接 main() 运行。
