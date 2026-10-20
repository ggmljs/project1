# 成员四：IDA*、Zobrist 哈希与转置表交接说明

## 1. 本模块包含的文件

正式代码：

- `src/stud/g01/hash/ZobristHash.java`（Zobrist 哈希表 + 增量更新）
- `src/stud/g01/hash/TranspositionTable.java`（转置表 / 哈希去重模块）
- `src/stud/g01/solver/IdAStar.java`（IDA* 正式实现：阈值迭代 + 深度优先 + 路径去环 + 转置表）
- `src/stud/solver/IdAStar.java`（框架接线适配类；成员三创建，本次升级为正式实现的适配层，未改动框架文件）

自测代码（不依赖 JUnit，直接运行 `main()`）：

- `test/stud/g01/hash/ZobristHashSelfTest.java`
- `test/stud/g01/hash/TranspositionTableSelfTest.java`
- `test/stud/g01/solver/IdAStarSelfTest.java`

实验工具：

- `test/stud/g01/runner/IdAStarStageTwoBenchmark.java`（第二阶段数据生成工具）

新增数据文件：

- `resources/problems15.txt`（7 个 15 数码实例，其中最后一行是实验要求文档给出的 4 阶实例）

本模块负责**第二阶段**：对 4 阶（15-Puzzle）使用 IDA*，并交付
“IDA* 版本、哈希去重模块、第二阶段数据”。

## 2. 第二阶段的任务与框架落点

### 2.1 任务

课程要求：

> 第二阶段：对于 4 阶的（15-Puzzle），使用 IDA*，解决相同的问题实例。建议使用 Zobrist 哈希。

本模块的交付物对应为：

1. 可运行的 IDA*（`IdAStar`）；
2. 哈希去重模块（`ZobristHash` + `TranspositionTable`）；
3. 15 数码的第二阶段实验数据（第 9 节）。

### 2.2 框架里的 IDA* 入口

框架 `core/runner/EngineFeeder.java` 的 `getIdaStar()` 是固定写法：

```java
public final AbstractSearcher getIdaStar(HeuristicType type) {
    Predictor predictor = getPredictor(type);
    Frontier frontier = new StackFrontier();
    return new IdAStar(frontier, predictor);   // 导入的是 stud.solver.IdAStar
}
```

因此接线关系是：

```text
PuzzleFeeder.getIdaStar(MANHATTAN)
        │ getPredictor(MANHATTAN)        → ManhattanPredictor
        │ new StackFrontier()
        ▼
stud.solver.IdAStar(frontier, predictor)  ← 适配类，只继承构造
        ▼
stud.g01.solver.IdAStar.search(problem)   ← 本模块的正式实现
        │ problem.solvable / root / childNodes / goal
        ▼
PuzzleBoard.next + NPuzzleProblem.applicable + ManhattanPredictor
```

`stud.solver.IdAStar` 只保留 `(Frontier, Predictor)` 构造函数，用来满足框架的
import 和调用；算法本体全部在 `stud.g01.solver.IdAStar`，这样成员四的代码都集中在
`stud.g01` 包下，同时满足“框架已有代码不要修改”的要求。

> 说明：成员三交接文档 8.1 节给出的两种方案，本模块采用了等价变体：
> 正式实现放在 `stud.g01.solver`，`stud/solver/IdAStar.java` 改成继承正式实现的适配类。
> 框架文件一个字节都没有改动；只改动了成员三创建的那个临时接线类。

### 2.3 框架 `SearchTester` 不会自动跑第二阶段

`core/runner/SearchTester.getHeuristicTypes()` 只处理：

- `PATHFINDING`
- `NPUZZLE` + `step == 1`（MISPLACED、MANHATTAN）
- `NPUZZLE` + `step == 3`（DISJOINT_PATTERN）

**没有 `step == 2` 的分支**，所以：

```text
java core.runner.SearchTester resources/problems.txt NPUZZLE 2 stud.g01.runner.PuzzleFeeder
```

不会真正搜索（启发函数列表为空）。这是框架本身的设计，不是 IDA* 的问题。
第二阶段统一用本模块的实验工具运行：

```text
java -cp out stud.g01.runner.IdAStarStageTwoBenchmark
```

## 3. IDA* 算法

### 3.1 为什么 15 数码用 IDA* 而不是 A*

| | A*（框架 `BestFirstSearcher`） | IDA*（本模块） |
| --- | --- | --- |
| 保存什么 | Open 表（`Frontier`）+ Closed 表（`explored`），保存所有已生成结点 | 只保存当前 DFS 路径（`Node` 的父链） |
| 空间 | O(已生成结点数) | O(解的长度) + 转置表 |
| 时间 | 每个状态扩展至多一次 | 阈值每次提高都要从头重搜，同一状态可能被重复访问 |
| 最优性 | 启发函数可采纳时最优 | 启发函数可采纳时最优 |
| 适用 | 解短、状态空间小（8 数码） | 解长、状态空间大（15 数码，最优解 50～80 步） |

15 数码的状态数是 16!/2 ≈ 1.05×10^13，最优解又长达 50～80 步；A* 在 15 数码上
会把海量结点同时放进内存，课程也把 4 阶明确放在 IDA* 阶段。IDA* 用“重复搜索”
换“常数空间”，是这类问题的经典选择。

### 3.2 阈值迭代流程

记 `g(n)` 为根到 n 的路径代价（每步 1），`h(n)` 为启发函数，`f(n) = g(n) + h(n)`。

```text
threshold = h(初始状态)                 // 任何解的长度都不小于它
循环：
    本轮最小超限值 next = +∞
    从根做 DFS：
        dfs(n, g):
            f = g + h(n)
            若 f > threshold:
                next = min(next, f)     // 记下下一轮的阈值
                返回
            若 n 已在当前路径上（环）：返回
            若转置表显示本轮已用 g' ≤ g 访问过 n：返回
            转置表记录 n 本轮的最小 g
            若 n 是目标：返回成功
            对每个合法子结点：压入路径 → dfs(child, g+1) → 弹出路径
    若找到目标：返回路径
    若 next = +∞：说明状态空间已搜完，无解
    threshold = next                    // 阈值提高，进入下一轮
```

实现上 `dfs()` 是递归函数，`nextThreshold`、路径栈 `pathKeys` 是搜索器的字段，
`search()` 负责外层阈值循环。

### 3.3 正确性与终止性

1. **可采纳性 ⇒ 最优性**：曼哈顿距离不高估真实剩余代价，所以任何满足
   `f(n) ≤ threshold` 的路径都不会比 threshold 长。第 k 轮结束时如果没找到解，
   说明最优解长度大于当前阈值；阈值按“被剪掉的最小 f”提高，因此第一次找到的
   解就是最优解。
2. **一致性 ⇒ 阈值严格递增**：曼哈顿距离满足三角不等式（一步移动最多让 h 变化 1），
   所以子结点的 f 不会比父结点小，`next > threshold` 恒成立；实现里还加了
   `next <= threshold` 的防御性检查，避免启发函数不满足一致性时死循环。
3. **奇偶性**：15 数码每走一步，曼哈顿距离改变 ±1，所以
   “解的长度 ≡ h(初始) (mod 2)”，阈值实际按 2 递增。数据里 h(初始)=44 的
   80 步实例，阈值依次是 44、46、48……
4. **终止**：阈值每轮严格增大且不可能超过最长简单路径，状态空间有限，
   最终要么找到解，要么一整轮没有任何结点超过阈值（`next = +∞`）而返回无解。

### 3.4 复杂度

- 时间：O(所有满足 f ≤ C* 的结点数)，其中 C* 是最优解长度；最坏情况仍是指数级，
  但比朴素 DFS 少得多。实测数据见第 9 节。
- 空间：O(C*) 的路径栈 + 转置表（2^22 槽位约 64 MiB）+ 递归深度（≤ 100），
  与解的长度同阶，不随搜索规模增长。
- 迭代次数：约 `(C* - h(初始))/2 + 1` 轮，每轮都从根重搜。

## 4. Zobrist 哈希

### 4.1 构造

为每个“棋子编号 × 格子编号”组合预生成一个 64 位随机数
`random[tile][cell]`，棋盘的哈希值是所有“棋子在其所在格子”的随机数的异或：

```text
key(board) = XOR_{cell} random[ board[cell] ][ cell ]
```

- 随机表用固定种子 `SplittableRandom` 生成，同一棋盘边长下每次运行结果完全一致，
  实验和自测可复现；
- 不同边长（3 阶 / 4 阶）用不同的随机表，互不干扰；
- 随机表按边长缓存在静态 `Map` 中，多个搜索共享。

### 4.2 增量更新

空格从格子 `from` 移到 `to`（`to` 上的棋子是 `tile`）时，只有两个格子变化：

```java
key ^= random[0][from]      // 空格离开 from
     ^ random[tile][from]   // tile 进入 from
     ^ random[tile][to]     // tile 离开 to
     ^ random[0][to];       // 空格进入 to
```

一次移动只做 4 次异或和 4 次数组访问，不必重新扫描 16 个格子。
`IdAStar` 在 DFS 中把父状态的 key 传给子状态，用的就是
`ZobristHash.afterMove(key, board, move)`。

### 4.3 碰撞

64 位哈希的碰撞概率约为 2^-64。为了不把“理论上可能”当成“实际不会发生”，
`ZobristHashSelfTest` 做了两项检查：

1. 从目标状态 BFS 枚举 8 数码全部 **181440** 个可达状态，所有状态的 key
   互不相同；
2. 15 数码随机游走 **100000** 个状态，用 `Map<key, board>` 检查：一旦 key 重复，
   比较两个棋盘是否真的相同，实测没有出现不同棋盘共享同一个 key。

Zobrist 只用于“快速判断是否同一状态”，不能替代 `PuzzleBoard.equals()`：
`PuzzleBoard` 的 `equals/hashCode` 保持不变（成员一文档第 8 节的要求）。

## 5. 转置表（哈希去重模块）

### 5.1 为什么需要

IDA* 的 DFS 每轮都是从头搜的树搜索。同一个棋盘状态可能经不同移动顺序到达
（transposition），如果每次都把它的子树重搜一遍，结点数会成倍增加。
转置表按 Zobrist key 记录“本轮以多大的 g 访问过该状态”，从而直接剪掉
重复的子树。

### 5.2 表项语义：只在“本轮”有效

表项是 `(Zobrist key, g)`，语义是：

> 在本轮阈值下，已经以路径代价 g 展开过该状态，它的整棵子树都已按当前阈值搜完。

因此在同一轮里，若再次以 `g' ≥ g` 到达该状态，子树完全相同，可以剪枝；
若以 `g' < g` 到达，更短的路径可能带来更好的解，必须更新表项并重新展开。

**为什么每轮要失效**：上一轮被阈值截断的子树，这一轮阈值变大后必须重新探索，
旧的“已搜完”结论不再成立。`TranspositionTable.beginIteration()` 把代数加一，
旧表项自动失效（惰性清除，不做 O(表大小) 的 `Arrays.fill`）；每轮重新记录。

> 这一点很容易写错：如果把“最小 g”跨轮保存、并在 `g' == g` 时直接剪枝，
> 新一轮里同一条最优路径会被上一轮的表项剪掉，搜索会漏解。
> 本实现严格按“每轮一代”处理，`IdAStarSelfTest` 里用已知最优解回归。

### 5.3 实现细节

- 固定大小开放定址表：`2^tableBits` 个槽位，每桶 2 槽，桶内线性比较；
- 哈希映射前先做 splitmix64 混合，避免低位规律造成分布不均；
- 新表项优先放空槽；桶满时替换 g 最大的槽（g 大的表项剪枝能力最弱）；
- 默认 `tableBits = 22`：`(8 + 4 + 4) 字节 × 2^22 ≈ 64 MiB`；
- 统计接口：`stores()`、`lookups()`；`IdAStar.transpositionHits()` 记录被剪掉的结点数。

表大小的影响（同一实例、同一启发函数，单次运行）：

| 实例 | tableBits=20（16 MiB） | tableBits=22（64 MiB） | tableBits=24（256 MiB） |
| --- | ---: | ---: | ---: |
| 15 数码 #1（52 步） | 1,131,466 / 179 ms | 1,131,119 / 191 ms | 1,131,095 / 217 ms |
| 15 数码 #2（51 步） | 6,013,577 / 699 ms | 5,997,673 / 749 ms | 5,996,422 / 771 ms |
| 15 数码 #4（57 步） | 117,384,991 / 12370 ms | 102,127,324 / 12075 ms | 97,836,372 / 12025 ms |

（格式为“生成结点 / 用时”。结点数是确定值，用时是单次测量。）

表越大命中越多，但收益递减，且表本身占内存；默认 64 MiB 在“剪枝收益”
和“内存占用”之间比较平衡。8 数码状态空间只有 18 万，用 `Options` 把
`tableBits` 调到 18 即可，不必占 64 MiB。

## 6. 路径去环

DFS 只保留一条路径，环检测只需要判断“子状态是否已经出现在当前路径上”：

```text
pathKeys[0..pathSize) 保存当前路径上各状态的 Zobrist key
childKey = afterMove(父 key, 棋盘, 动作)
若 childKey 在 pathKeys 中：剪枝（cyclePrunes++）
否则压栈、递归、弹栈
```

- 路径长度就是解的长度（15 数码 ≤ 80），线性扫描比新建 `HashSet` 更便宜，
  也没有 `Long` 装箱；
- 剪掉环不会丢最优解：任何带重复状态的移动序列都可以删掉环得到一个更短的
  合法序列，因此最优路径一定是简单路径；
- 它是转置表之外的第二道保险：即使关闭转置表（消融实验），搜索也不会陷入
  “空格来回移动”的死循环。

## 7. 代码接口

### 7.1 构造函数与 `Options`

```java
// 框架 EngineFeeder.getIdaStar() 使用
new IdAStar(frontier, predictor);

// 带参数，供实验使用
new IdAStar(frontier, predictor, new IdAStar.Options(useTable, tableBits, timeLimitMillis));
```

`Options` 三个参数：

| 参数 | 含义 | 默认值 |
| --- | --- | --- |
| `transpositionTable` | 是否启用转置表 | `true` |
| `tableBits` | 转置表槽位数的二进制位数（8～26） | `22` |
| `timeLimitMillis` | 单实例时间上限（毫秒），0 表示不限 | `0` |

常用工厂：

```java
Options.withoutTranspositionTable()      // 关表，保留路径去环
new Options(true, 22, 0).withTableBits(18)
new Options(true, 22, 0).withTimeLimitMillis(120_000)
```

### 7.2 统计接口

`search()` 返回后可以读取：

```java
int  iterations()          // 阈值迭代轮数
int  thresholdLimit()      // 最近一轮的阈值（成功时 = 解的长度）
long transpositionHits()   // 被转置表剪掉的结点数
long cyclePrunes()         // 被路径去环剪掉的结点数
boolean timedOut()         // 是否触发时间上限
int  nodesGenerated()      // 继承自框架 AbstractSearcher
int  nodesExpanded()
```

注意：`timedOut() == true` 时 `search()` 也返回 `null`，必须和“无解”区分开；
实验工具里对这两种情况分别标注。

### 7.3 与成员五（模式数据库）的接线

IDA* 只依赖 `Predictor` 接口，第二阶段用曼哈顿距离，第三阶段可以直接复用：

```java
Predictor pdb = new DisjointPatternPredictor(...);       // 成员五实现
AbstractSearcher ida = new IdAStar(new StackFrontier(), pdb, new Options(true, 22, 0));
```

如果成员五按成员三留下的接入点改 `PuzzleFeeder.getPredictor(DISJOINT_PATTERN)`，
那么 `feeder.getIdaStar(DISJOINT_PATTERN)` 会自动变成“IDA* + 模式数据库”，
不需要改本模块的代码。

## 8. 编译、运行与自测

### 8.1 编码（重要）

框架 `.java` 是 **GBK**，成员一～四新增的文件是 **UTF-8**。命令行一次性编译会报
“不可映射字符”：

```text
javac -encoding GBK ...   # 成员的文件会被读错
javac -encoding UTF-8 ... # 框架的文件会被读错
```

两种处理方式：

- IDEA：`File → Settings → Editor → File Encodings` 建议整体设为 UTF-8
  （IDEA 会按文件实际编码打开，乱码不影响编译）；也可以只把 `src/core`、
  `src/stud/problem`、`src/stud/queue`、`src/stud/runner`、`src/algs4` 设为 GBK。
- 命令行：先把 GBK 文件转成 UTF-8，再统一用 `-encoding UTF-8` 编译。
  本模块验证时是在临时目录里按文件自动识别编码、统一转 UTF-8 后编译运行的。

### 8.2 运行

以下命令都在仓库根目录执行，`out` 是编译输出目录：

```text
# 第二阶段完整数据（8 数码 A*/IDA* 对比 + 15 数码，单实例上限 120 s）
java -Xmx4g -cp out stud.g01.runner.IdAStarStageTwoBenchmark --timeout 120

# 额外做“有/无转置表”的消融
java -Xmx4g -cp out stud.g01.runner.IdAStarStageTwoBenchmark --timeout 120 --ablation

# 用随机 8 数码实例做 A*/IDA* 对比（固定种子，可复现）
java -Xmx4g -cp out stud.g01.runner.IdAStarStageTwoBenchmark --random8 10

# 指定实例文件
java -Xmx4g -cp out stud.g01.runner.IdAStarStageTwoBenchmark --file15 resources/problems15.txt
```

Windows 控制台如果中文乱码，先执行 `chcp 65001`，或者把输出重定向到文件再查看。

### 8.3 自测

```text
java -cp out stud.g01.hash.ZobristHashSelfTest
java -cp out stud.g01.hash.TranspositionTableSelfTest
java -cp out stud.g01.solver.IdAStarSelfTest
```

预期输出：

```text
ZobristHashSelfTest: all tests passed. checks=623
TranspositionTableSelfTest: all tests passed. checks=21
IdAStarSelfTest: all tests passed. checks=46
```

覆盖内容：

- `ZobristHash`：随机表复用与可复现、相同棋盘内容得到相同 key、每一步移动都改变 key、
  增量哈希与从头计算一致（3/4 阶各 300 步随机游走）、8 数码全部 181440 个状态无碰撞、
  15 数码 10 万随机状态无碰撞、非法入参；
- `TranspositionTable`：空表、写入/读取、同状态保留最小 g、换代失效、clear、
  1000 个表项全部可读、非法参数；
- `IdAStar`：初始即目标（空路径）、一步可解、给定 8 数码实例 #1（21 步）、
  4 个随机 8 数码实例与 A* 的最优长度一致、14-15 互换无解被拦截、
  关闭转置表仍得最优解、`EngineFeeder.getIdaStar` 接线可用、时间上限、
  非法入参（含非 N-Puzzle 状态）。

## 9. 第二阶段实验数据

运行环境：Windows 11，JDK 21，`-Xmx4g`，单次运行，JVM 已预热。
**生成/扩展结点数是确定值，每次运行完全一致；用时是单次墙钟测量，
不同机器/不同预热状态会有 10%～30% 波动，正式报告建议多跑几次取中位数。**

实例来源：

- 8 数码：`resources/problems8.txt`（与 `problems.txt` 前 3 行相同）；
- 15 数码：`resources/problems15.txt`（取自 `problems3.txt` 第 4～10 行，
  最后一行是实验要求文档给出的 4 阶实例）。

### 9.1 8 数码：A* 与 IDA* 对比（同一曼哈顿启发函数）

| 实例 | 搜索 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 迭代/转置命中 | 解校验 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| #1 | A* | 13 | 21 | 4.596 | 2489 | 931 | - | 通过 |
| #1 | IDA* | 13 | 21 | 3.389 | 2427 | 912 | 5/32 | 通过 |
| #2 | A* | 21 | 31 | 28.698 | 55925 | 21197 | - | 通过 |
| #2 | IDA* | 21 | 31 | 9.453 | 31805 | 12151 | 6/762 | 通过 |
| #3 | A* | 21 | 31 | 14.568 | 55925 | 21197 | - | 通过 |
| #3 | IDA* | 21 | 31 | 3.601 | 25514 | 9762 | 6/639 | 通过 |

| 搜索 | 实例数 | 成功 | 平均用时(ms) | 平均生成结点 | 平均扩展结点 |
| --- | ---: | ---: | ---: | ---: | ---: |
| A* | 3 | 3 | 16.0 | 38113 | 14441 |
| IDA* | 3 | 3 | 5.5 | 19915 | 7608 |

观察：

1. 两者的解长完全一致（21、31、31），说明 IDA* 的“最优性”没有因为重复搜索而丢失；
2. IDA* 生成结点反而更少：#2 从 55925 降到 31805（−43%），#3 从 55925 降到 25514（−54%）。
   原因是 A* 要把 f < 最优解的所有结点都展开完，而 IDA* 的转置表把同一轮的
   transposition 剪掉了；
3. 把转置表按 8 数码的规模调成 2^18（4 MiB）后，IDA* 在三个实例上的墙钟都优于 A*
   （3.4 / 9.5 / 3.6 ms 对 4.6 / 28.7 / 14.6 ms）：结点更少，单结点多做一次
   哈希查找的代价被抵消；
4. 真正的差别仍在空间：A* 的 Open/Closed 随结点数增长，IDA* 的额外内存是固定大小的
   转置表 + 与解长同阶的路径栈；15 数码上这个差别是决定性的（A* 需要保存上亿结点）。

### 9.2 15 数码：IDA*（单实例上限 120 s）

| 实例 | h(初始) | 解长 | 用时(ms) | 生成结点 | 扩展结点 | 迭代轮数 | 转置命中 | 状态 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| #1 | 44 | 52 | 166.0 | 1,131,119 | 379,583 | 5 | 19,207 | 通过 |
| #2 | 35 | 51 | 669.3 | 5,997,673 | 2,002,435 | 9 | 135,752 | 通过 |
| #3 | 38 | 56 | 4,484.7 | 38,533,603 | 13,143,563 | 10 | 1,140,286 | 通过 |
| #4 | 41 | 57 | 12,127.9 | 102,127,324 | 33,987,299 | 9 | 3,341,330 | 通过 |
| #5 | 34 | 50 | 1,662.9 | 14,264,797 | 4,754,309 | 9 | 417,019 | 通过 |
| #6 | 43 | 61 | 74,080.1 | 594,407,948 | 202,810,162 | 10 | 17,922,193 | 通过 |
| #7（80 步实例） | 44 | - | 120,000.2 | 1,033,521,947 | 349,374,464 | 12 | 27,441,372 | 超时(>120s) |

（#7 的结点数是 120 s 内已经生成/扩展的数量，不是完整搜索的结点数。）

观察：

1. 7 个实例中 6 个在 120 s 内求出最优解，解长 50～61 步，全部通过“回放动作到达目标”的校验；
2. 搜索规模随解长迅速膨胀：50 步约 1.4×10^7 结点，57 步约 1.0×10^8，
   61 步约 5.9×10^8；结点数与解长近似呈指数关系；
3. 迭代轮数在 5～10 之间，与“阈值从 h(初始) 按 2 递增到最优解长度”的推算一致；
4. 转置命中数从 1.9 万到 1792 万，说明 transposition 在 15 数码里非常普遍，
   去重模块是规模能跑到 10^8 量级的关键之一（见 9.3）。

### 9.3 消融：转置表的作用

同一实例、同一启发函数，分别启用/关闭转置表（路径去环保留）：

| 实例 | 转置表 | 生成结点 | 用时(ms) | 解长 | 结点减少 |
| --- | --- | ---: | ---: | ---: | ---: |
| 8 数码 #1（h0=13） | 有 | 2,427 | 1.4 | 21 | 1.14x |
| 8 数码 #1（h0=13） | 无 | 2,763 | 0.3 | 21 | - |
| 8 数码 #2（h0=21） | 有 | 31,805 | 4.5 | 31 | 1.46x |
| 8 数码 #2（h0=21） | 无 | 46,527 | 4.8 | 31 | - |
| 8 数码 #3（h0=21） | 有 | 25,514 | 3.4 | 31 | 1.45x |
| 8 数码 #3（h0=21） | 无 | 36,978 | 3.2 | 31 | - |
| 15 数码 #1（52 步） | 有 | 1,131,119 | 151.7 | 52 | 2.66x |
| 15 数码 #1（52 步） | 无 | 3,004,776 | 208.5 | 52 | - |
| 15 数码 #2（51 步） | 有 | 5,997,673 | 687.7 | 51 | 1.86x |
| 15 数码 #2（51 步） | 无 | 11,181,742 | 765.4 | 51 | - |
| 15 数码 #3（56 步） | 有 | 38,533,603 | 4361.5 | 56 | 2.67x |
| 15 数码 #3（56 步） | 无 | 102,900,266 | 6972.1 | 56 | - |
| 15 数码 #4（57 步） | 有 | 102,127,324 | 11664.0 | 57 | 3.66x |
| 15 数码 #4（57 步） | 无 | 374,232,048 | 26365.7 | 57 | - |
| 15 数码 #5（50 步） | 有 | 14,264,797 | 1624.5 | 50 | 2.53x |
| 15 数码 #5（50 步） | 无 | 36,152,972 | 2482.7 | 50 | - |

观察：

1. 15 数码上转置表减少 **1.9～3.7 倍**的生成结点，墙钟时间减少 **1.2～2.3 倍**；
2. 8 数码上结点只减少 1.1～1.5 倍，墙钟基本持平（#1 是 1.4 ms 对 0.3 ms，
   小规模搜索里建表、查询和 JIT 预热的开销占了主导；#2/#3 则基本打平）：
   8 数码场景建议用 2^18 的小表（benchmark 就是这么做的）；
3. 关表后路径去环仍然工作（`cyclePrunes > 0`），说明两者职责不同：
   去环解决“同一条路径上的环”，转置表解决“不同路径到达同一状态”。

### 9.4 最坏情况实例：80 步的 15-Puzzle

实验要求文档给出的 4 阶实例：

```text
15 14 13 12
11 10  9  8
 7  6  5  4
 3  1  2  _
```

它是 15-Puzzle 的“最坏情况”实例，最优解 **80 步**。用曼哈顿距离 + IDA*：

- h(初始) = 44，阈值只能按 2 递增（44、46、48……）；
- 120 s 内完成 12 轮迭代（阈值最高只到约 66），生成约 **1.0×10^9** 个结点，
  扩展约 **3.5×10^8** 个结点，仍未到达 80；
- 从 50 步实例的 1.4×10^7 结点、57 步的 1.0×10^8 结点、61 步的 5.9×10^8 结点
  外推，80 步实例的规模要再大几个数量级。

结论：**纯曼哈顿距离的 IDA* 无法在可接受时间内解出 80 步实例**，这不是实现问题，
而是启发函数太松弛。实验要求把 4 阶实例分给第二阶段、并让第三阶段用
disjoint pattern database 在 1 分钟内求解，正是这个原因；模式数据库属于成员五，
本模块已经把 IDA* 的 `Predictor` 接口留好（7.3 节），第三阶段可以直接复用。

### 9.5 结论（报告可直接引用）

1. **IDA* 在 8 数码上与 A* 得到相同的最优解，结点数更少**（−43%～−54%），
   用时也更少（3.4/9.5/3.6 ms 对 4.6/28.7/14.6 ms），
   空间从 O(结点数) 降到 O(解长) + 固定大小的转置表。
2. **15 数码上 IDA* 可在 0.17～74 s 内解出 50～61 步的 6 个给定实例**，
   验证了第二阶段“使用 IDA* 解 4 阶问题”的可行性。
3. **Zobrist 增量哈希把状态指纹的计算降到 4 次异或**；转置表在同轮内剪掉
   1.9～3.7 倍重复子树，是规模能到 10^8 结点量级的关键；路径去环保证
   关闭转置表时也不会陷入环。
4. **启发函数是 15 数码的瓶颈**：80 步实例在 120 s 内只推进到约 66 的阈值。
   下一阶段的 disjoint pattern database 是必要的改进，而不是可选优化。
5. 三个阶段串起来看：A* + 曼哈顿解决 8 数码（毫秒级）→ IDA* + Zobrist
   解决中等难度 15 数码（秒级）→ 模式数据库解决最坏情况 15 数码（分钟级），
   改进方向是“启发函数越来越紧、搜索越来越省内存”。

## 10. 与其他成员的接口约定

- 只使用成员一的 `PuzzleBoard` 公开接口（`getSize/getBlankIndex/getTile/canMove/next`），
  没有修改 `PuzzleBoard.equals()` 或移动方向定义；
- 只使用成员二的 `NPuzzleProblem`（`solvable/root/childNodes/goal`），
  没有改问题层；
- 复用成员三的 `ManhattanPredictor` 与 `StackFrontier`，没有改启发函数；
- 框架文件（`core/**`、`stud/problem/**`、`stud/queue/**`、`stud/runner/**`）一个字节未改；
- 只把成员三创建的 `stud/solver/IdAStar.java` 从“抛异常的占位类”改成
  “继承成员四正式实现的适配类”，构造函数签名保持不变；
- 成员五接入模式数据库时，按 7.3 节即可；`SearchTester` 的 `step == 3`
  会通过 `PuzzleFeeder.getPredictor(DISJOINT_PATTERN)` 拿到预测器。

## 11. 职责边界

本模块已完成：

- IDA*（阈值迭代、DFS、目标检测、路径生成）；
- Zobrist 哈希（随机表、增量更新、碰撞检查）；
- 转置表（每轮一代的 g 去重、桶式替换、统计）；
- 路径去环；
- 三个自测 + 第二阶段 benchmark + 15 数码实例文件 + 实验数据；
- 框架 `stud.solver.IdAStar` 接线的正式化。

本模块**没有**实现（属于其他成员）：

- `PuzzleBoard` / `PuzzleMove`（成员一）；
- `NPuzzleProblem`（成员二）；
- `PqFrontier`、曼哈顿/错位启发函数、`PuzzleFeeder`、阶段一 A*（成员三）；
- Disjoint Pattern Database、阶段三实验、解路径可视化（成员五）。

## 12. 已知问题与后续工作

1. **80 步实例没有解出**（9.4 节）：曼哈顿距离太松弛，需要模式数据库；
   本模块不把超时结果当作“无解”，`timedOut()` 可以区分两者。
2. **耗时数据是单次测量**：结点数确定，时间受 JVM 预热影响明显（消融表里
   8 数码 #1 无表 0.3 ms、有表 1.4 ms，建表和预热的开销在小规模下占了主导）；
   正式报告建议每个配置跑 3～5 次取中位数。
3. **转置表默认 64 MiB（2^22）**：面向 15 数码；8 数码建议用 `Options` 调成
   2^18（4 MiB），否则建表开销会盖过剪枝收益（9.3 节）。
4. **递归深度**：DFS 递归深度等于解长，15 数码不超过 80，远低于 JVM 默认栈；
   如果以后扩展到 24 数码等更深的问题，需要改成显式栈或调大 `-Xss`。
5. **Zobrist 是概率性去重**：64 位下碰撞概率约 2^-64，已在 8 数码全部状态和
   10 万随机 15 数码状态上验证；如果要求绝对正确，可以在转置表里额外存
   棋盘的 32 位指纹或走 `PuzzleBoard.equals()` 复核。
6. **`SearchTester` 阶段二没有自动接线**（2.3 节）：这是框架设计，未修改；
   第二阶段统一用 `IdAStarStageTwoBenchmark` 运行，第三阶段仍可走
   `SearchTester ... NPUZZLE 3`。
