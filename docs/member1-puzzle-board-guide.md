# 成员一：PuzzleBoard 状态模型交接说明

## 1. 本模块包含的文件

正式代码：

- `src/stud/g01/problem/npuzzle/PuzzleBoard.java`
- `src/stud/g01/problem/npuzzle/PuzzleMove.java`

自测代码：

- `test/stud/g01/problem/npuzzle/PuzzleBoardSelfTest.java`

本模块负责 N-Puzzle 的棋盘状态、空格位置、移动动作、状态转换、状态比较和文本显示。

## 2. 最重要的统一约定

### 2.1 空格的表示

- 数字 `0` 表示空格。
- 文本输出时，空格显示为 `_`。

### 2.2 移动方向

`UP`、`DOWN`、`LEFT`、`RIGHT` 表示**空格的移动方向**，不是数字块的移动方向。

例如：

```text
1 2 3
4 _ 5
6 7 8
```

执行 `UP` 后变为：

```text
1 _ 3
4 2 5
6 7 8
```

后续的 `NPuzzleProblem`、IDA*、解路径输出和可视化必须使用同一方向定义。

### 2.3 动作的过滤流程

`PuzzleBoard.actions()` 返回四种候选动作：

```text
UP、DOWN、LEFT、RIGHT
```

动作是否适用于当前棋盘，应由 `NPuzzleProblem.applicable()` 调用
`PuzzleBoard.canMove()` 判断。

框架中的调用顺序为：

```text
PuzzleBoard.actions()
        ↓
NPuzzleProblem.applicable()
        ↓
PuzzleBoard.next()
```

不要在其他模块中重新编写另一套边界判断逻辑，否则容易出现移动规则不一致。

## 3. 创建棋盘

构造函数：

```java
public PuzzleBoard(int size, int[] tiles)
```

其中：

- `size` 是棋盘边长；
- `tiles` 是按行展开的一维数组；
- `tiles.length` 必须等于 `size * size`；
- 数字必须为 `0` 到 `size * size - 1`，且不能重复。

3×3 示例：

```java
PuzzleBoard board = new PuzzleBoard(3, new int[]{
        1, 2, 3,
        4, 0, 5,
        6, 7, 8
});
```

4×4 示例：

```java
PuzzleBoard board = new PuzzleBoard(4, new int[]{
        1, 2, 3, 4,
        5, 6, 7, 8,
        9, 10, 11, 12,
        13, 14, 15, 0
});
```

构造函数会检查棋盘数据并复制传入数组。调用者之后修改原数组不会影响棋盘。

## 4. PuzzleBoard 对外接口

### 4.1 棋盘和空格信息

```java
int getSize()
int getBlankIndex()
int getBlankRow()
int getBlankCol()
int getTile(int row, int col)
int getTile(int index)
int[] getTiles()
```

注意：

- 行、列和下标均从 `0` 开始；
- `getTiles()` 返回数组副本，修改返回值不会影响棋盘；
- 在启发函数的高频循环中，优先使用 `getTile(...)`；
- 不要反复调用 `getTiles()`，因为每次调用都会复制数组。

### 4.2 移动相关接口

```java
boolean canMove(PuzzleMove move)
Iterable<? extends Action> actions()
PuzzleBoard next(Action action)
```

`next()` 的特点：

- 合法移动会返回一个新的 `PuzzleBoard`；
- 原棋盘不会被修改；
- 非 `PuzzleMove` 动作会抛出 `IllegalArgumentException`；
- 越界移动会抛出 `IllegalArgumentException`。

因此必须先调用 `applicable()` 或 `canMove()`，再调用 `next()`。

### 4.3 显示和比较

```java
void draw()
String toString()
boolean equals(Object obj)
int hashCode()
```

`equals()` 和 `hashCode()` 已经按照棋盘内容实现，可直接用于：

```java
HashSet<State> explored
HashMap<PuzzleBoard, ...>
```

不要在棋盘创建后修改其内部状态。`PuzzleBoard` 已设计为不可变类。

## 5. PuzzleMove 对外接口

四个可复用动作常量：

```java
PuzzleMove.UP
PuzzleMove.DOWN
PuzzleMove.LEFT
PuzzleMove.RIGHT
```

方向也可以通过下面的方法读取：

```java
PuzzleMove.Direction getDirection()
```

每次合法移动的代价为 `1`：

```java
move.stepCost() == 1
```

建议优先使用上面的四个常量，无须重复创建动作对象。

## 6. 成员二接入 NPuzzleProblem

### 6.1 applicable 示例

```java
@Override
public boolean applicable(State state, Action action) {
    if (!(state instanceof PuzzleBoard)
            || !(action instanceof PuzzleMove)) {
        return false;
    }

    PuzzleBoard board = (PuzzleBoard) state;
    PuzzleMove move = (PuzzleMove) action;
    return board.canMove(move);
}
```

### 6.2 stepCost 示例

```java
@Override
public int stepCost(State state, Action action) {
    if (!(action instanceof PuzzleMove)) {
        throw new IllegalArgumentException("action must be a PuzzleMove");
    }
    return action.stepCost();
}
```

### 6.3 showSolution 注意事项

搜索路径中的动作同样表示空格移动方向。输出文本时建议同时保存：

- 动作；
- 移动后的棋盘；
- 当前步数。

这样后续可视化程序可以直接读取状态序列或动作序列。

## 7. 成员三接入启发函数

错位将牌数和曼哈顿距离均不要计算空格 `0`。

遍历棋盘时建议使用：

```java
for (int row = 0; row < board.getSize(); row++) {
    for (int col = 0; col < board.getSize(); col++) {
        int tile = board.getTile(row, col);
        if (tile == 0) {
            continue;
        }

        // 计算该数字的启发值
    }
}
```

不要在内层循环反复调用 `getTiles()`，避免产生大量数组副本。

## 8. IDA*、Zobrist 和 Pattern Database 接入注意事项

- 可以使用 `getTile(index)` 按一维位置遍历棋盘；
- 可以使用 `getBlankIndex()` 快速获得空格位置；
- `PuzzleBoard` 的普通 `hashCode()` 用于 Java 集合去重；
- Zobrist Hash 应由 IDA* 模块单独计算，不能用 Zobrist 值替换 `equals()`；
- 即使两个状态的 Zobrist 值相同，也不一定代表棋盘内容相同；
- Pattern Database 需要的抽象状态编码应在其模块内实现，不要修改 `PuzzleBoard.equals()`。

## 9. 自测方法

测试入口：

```text
test/stud/g01/problem/npuzzle/PuzzleBoardSelfTest.java
```

该测试不依赖 JUnit，直接运行其中的 `main()` 即可。

预期输出：

```text
PuzzleBoardSelfTest: all tests passed.
```

测试覆盖：

- 上、下、左、右四个方向；
- 边界动作判断；
- 非法动作拒绝；
- 逆向移动恢复原状态；
- 移动后父状态保持不变；
- 构造参数和返回数组的防御性复制；
- `equals()`、`hashCode()` 和 `HashSet` 去重；
- 重复数字状态拒绝；
- 3×3 文本输出；
- 4×4 棋盘和两位数显示。

如果 IDEA 没有识别 `test` 目录，可以右键该目录，选择：

```text
Mark Directory as → Test Sources Root
```

## 10. 职责边界

成员一已经完成：

- `PuzzleBoard`；
- `PuzzleMove`；
- 棋盘状态转换和测试。

本模块没有实现：

- N-Puzzle 可解性判断；
- `NPuzzleProblem`；
- `PuzzleFeeder`；
- 错位将牌数和曼哈顿距离；
- `PqFrontier`；
- A*、IDA*；
- Zobrist Hash；
- Disjoint Pattern Database。

后续成员如果认为必须修改 `PuzzleBoard` 的公开接口或移动定义，请先在组内确认，避免其他模块同时依赖不同版本。

## 11. 当前已知的项目级问题

成员一模块已经独立编译并通过自测，但整个项目暂时不能仅依靠成员一代码运行完整的 `SearchTester`，因为其他成员负责的类仍为空实现，并且课程框架中的 IDA* 包名和阶段二入口还需要后续进行最小接线修复。

该问题不属于 `PuzzleBoard` 状态模型错误。
