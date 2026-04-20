# Latest Progress

本文档记录当前分支里“单植物 + 单路径 + 单区块进度”最小原型的最新状态，方便后续继续开发时快速恢复上下文。

## 当前结论

- 最小闭环已经打通：
  - 区块加载时可以根据中心群系与气候匹配 `ecoflux:plains_to_forest`
  - 区块附件会保存当前群系、目标群系、进度、活跃路径、植物队列和活跃植物
  - 原型植物可以成功放置并计入点数
  - 区块进度满后会真实把整个区块的 biome 改成 `minecraft:forest`
- 当前原型已经从“纯自动黑盒”调整为“命令可调试 + 可选自动 tick”双模式
- 自动模式已经恢复，并且复用命令调通后的那套底层逻辑

## 当前原型范围

- 重点原型路径：`ecoflux:plains_to_forest`
- 当前原型植物：`minecraft:dandelion`
- 当前目标：验证单路径、单植物、单区块进度的完整运行链路，而不是正式平衡性

## 当前代码落点

- 主入口：`src/main/java/com/s/ecoflux/EcofluxMod.java`
- 区块附件：`src/main/java/com/s/ecoflux/attachment/SuccessionChunkData.java`
- 配置加载：`src/main/java/com/s/ecoflux/config/SuccessionConfigLoader.java`
- 原型核心逻辑：`src/main/java/com/s/ecoflux/prototype/PrototypeChunkController.java`
- 区块事件与自动 tick：`src/main/java/com/s/ecoflux/init/ModChunkEvents.java`
- 调试命令：`src/main/java/com/s/ecoflux/init/ModCommands.java`
- 原型路径配置：`src/main/resources/data/ecoflux/succession_paths/plains_to_forest.json`

## 已实现能力

### 1. 区块初始化

- 区块加载时会采样区块中心 biome、温度、downfall
- 使用 `SuccessionConfigRegistry.findBestMatch(...)` 匹配路径
- 匹配成功后会初始化：
  - `currentBiome`
  - `targetBiome`
  - `previousBiome`
  - `activePathId`
  - `consumingValue`
  - `maxPlantCount`
  - `plantQueue`

### 2. 原型植物队列

- `plains_to_forest` 当前是单植物原型
- 初始化后会生成队列
- 队列为空时也会自动补回当前原型植物条目

### 3. 植物种植与跟踪

- 会在区块内寻找合法列与可放置高度
- 成功种下后写入 `activePlants`
- 会记录：
  - 植物 id
  - 坐标
  - pointValue
  - 出生时间
  - 失效时间
  - 来源 biome

### 4. 进度评估

- 植物总点数来自 `activePlants`
- `totalPlantPoints >= consumingValue` 时按正步长增加进度
- 否则按负步长降低进度
- 达到 `>= 1.0` 时触发区块 biome 转化

### 5. 真实 biome 转化

- 当前实现会直接对目标区块重新填充 biome 数据
- 完成后会向该维度玩家广播 biome 更新包
- 转化后会清理原型路径状态和运行态数据

## 当前命令

以下命令默认作用于“玩家脚下所在区块”：

- `/ecoflux prototype init`
  - 重新初始化当前区块并重建原型状态
- `/ecoflux prototype status`
  - 查看当前区块状态
- `/ecoflux prototype prune`
  - 清理失效追踪植物
- `/ecoflux prototype spawn`
  - 手动尝试种植一次
- `/ecoflux prototype evaluate`
  - 手动做一次进度评估
- `/ecoflux prototype step`
  - 一次执行 `prune + spawn + evaluate`
- `/ecoflux prototype transition`
  - 强制当前区块立刻完成 biome 转化

### 自动模式命令

- `/ecoflux prototype auto on`
  - 开启自动 tick 处理
- `/ecoflux prototype auto off`
  - 关闭自动 tick 处理
- `/ecoflux prototype auto status`
  - 查看自动模式当前是否开启

## 当前配置项

`plains_to_forest.json` 的 `chunk_rules` 目前支持：

- `consuming`
- `max_plant_count`
- `queue_fill_factor`
- `evaluation_interval_days`
- `processing_interval_ticks`
- `evaluation_interval_ticks`
- `positive_progress_step`
- `negative_progress_step`

其中当前原型实际主要依赖：

- `processing_interval_ticks`
- `evaluation_interval_ticks`
- `positive_progress_step`
- `negative_progress_step`

## 已验证结果

- 手动命令模式已验证成功：
  - `init` 正常初始化
  - `spawn` 可以成功放置原型植物
  - `evaluate` 可以推进进度
  - `transition` 可把当前区块改成 `minecraft:forest`
- 自动模式已重新接回，并且编译通过
- 本地已验证 `.\gradlew.bat compileJava` 成功

## 已知现状与限制

- 当前仍然是原型实现，不是正式玩法版本
- 当前只重点保证 `plains_to_forest` 的最小闭环
- 玩家手动种下的植物还没有自动计入 `activePlants`
- 目前的自动模式仍是原型级调度，还没有做更细的负载控制和更广泛的数据同步设计
- 当前 biome 转化是“整块直接切换”，边界混合尚未开始做

## 建议的下一步

- 保留当前命令体系，继续作为调试入口
- 在自动模式稳定后，扩回多植物队列与权重抽取
- 把玩家自然种植/破坏/消失事件接入 `activePlants`
- 扩展更多路径，并验证同源多分支匹配
- 再考虑网络同步、边界处理和兼容层
