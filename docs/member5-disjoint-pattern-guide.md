# 成员五：不相交模式数据库与路径展示交接说明

## 1. 本次完成的文件

正式代码：

- `src/stud/g01/heuristic/DisjointPatternPredictor.java`
- `src/stud/g01/visualization/PuzzleSolutionRenderer.java`
- `src/stud/g01/runner/PuzzleFeeder.java`（接入 `DISJOINT_PATTERN`）
- `src/stud/g01/problem/npuzzle/NPuzzleProblem.java`（复用统一渲染器并暴露目标棋盘）

自测与实验工具：

- `test/stud/g01/heuristic/DisjointPatternPredictorSelfTest.java`
- `test/stud/g01/runner/StageThreeBenchmark.java`

## 2. 模式数据库原理

每个模式只关注一组数字和空格，把其他数字当作不可区分的背景。例如 15-Puzzle 默认划分为：

```text
{1, 2, 3, 4} + {5, 6, 7, 8} + {9, 10, 11, 12} + {13, 14, 15}
```

构建数据库时从目标抽象状态反向搜索：

- 空格与模式数字交换，代价为 1；
- 空格与背景数字交换，代价为 0；
- 使用 0-1 BFS 计算每个抽象状态到目标的最短代价。

各模式没有重复数字，因此每个真实移动最多只为一个模式付出代价，所有模式距离之和不会高估真实最优解，是可用于 A* 和 IDA* 的可采纳启发式函数。

## 3. 默认模式划分

- 3×3：4 + 4；
- 4×4：4 + 4 + 4 + 3。

每个抽象状态由“空格位置 + 模式数字位置”编码为整数。数据库使用自定义开放寻址表保存，避免大量 `Integer`/`Long` 装箱对象。相同目标棋盘的数据库会被静态缓存，不会在每次调用启发函数时重复构建。

## 4. 框架接入

`PuzzleFeeder.getPredictor(HeuristicType.DISJOINT_PATTERN)` 现在返回 `DisjointPatternPredictor`，因此可以直接运行第三阶段入口：

```text
resources/problems3.txt NPUZZLE 3 stud.g01.runner.PuzzleFeeder
```

`SearchTester` 在第三阶段会选择 `DISJOINT_PATTERN`，并继续使用已有的 A* 框架。

## 5. 路径展示

`PuzzleSolutionRenderer` 将路径统一输出为：

```text
Step 0: 初始棋盘
Step 1 (action: RIGHT): 移动后的棋盘
...
Total steps: 最终步数
```

输出可以直接打印，也可以写入 UTF-8 文本文件，后续如果需要 Unity 或 SFML，可将每个 Step 解析为动画帧。

## 6. 自测与性能测试

直接运行：

```text
test/stud/g01/heuristic/DisjointPatternPredictorSelfTest.java
```

该测试覆盖目标状态、一步状态、PuzzleFeeder 接入、21 步 8-Puzzle 最优解和路径终点校验。

第三阶段轻量测试入口：

```text
java -cp out stud.g01.runner.StageThreeBenchmark
```

也可以指定输入文件、最大棋盘阶数和第一个解的输出文件：

```text
java -cp out stud.g01.runner.StageThreeBenchmark resources/problems3.txt 3 solution.txt
```

默认只运行 8-Puzzle，避免直接用 A* 对全部 15-Puzzle 实例造成过高内存消耗。

## 7. 注意事项

- 当前实现支持课程要求的 8-Puzzle 和 15-Puzzle，超过 4×4 会明确拒绝；
- PDB 第一次遇到某个目标布局时会有一次构建开销，之后由缓存复用；
- PDB 是 A* 第三阶段启发式，不替换成员四的 IDA*、Zobrist 和转置表；
- `PuzzleBoard` 的 `equals/hashCode` 和移动方向约定保持不变；
- 若要扩展为图形界面，只需读取 `PuzzleSolutionRenderer` 生成的状态序列，不需要修改搜索算法。
