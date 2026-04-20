# Latest Progress

本文档记录当前分支里“单植物 + 单路径 + 单区块进度”最小原型的最新状态，方便后续继续开发时快速恢复上下文。

## 当前结论

- 最小闭环仍然成立：
  - 区块加载时可匹配 `ecoflux:plains_to_forest`
  - 原型植物可以放置
  - 进度满后会把整个区块 biome 切到 `minecraft:forest`
- 自动模式已经恢复，并继续复用命令调通后的底层逻辑。
- 客户端视觉生命周期层已经不再只是命令玩具，**现在已接入 plant lifecycle system 的最小自动通路**：
  - 服务端 `VegetationTracker` 维护 `ActiveVegetationRecord`
  - 自动 tick / 调试命令会推进 `observeChunk(...)`
  - 服务端会把区块内生命周期视觉快照同步给客户端
  - 客户端 `VisualLifecycleClientRuntime` 会直接消费外部阶段/进度做 scale 与 aging tint
- 当前视觉层仍保持独立抽象：
  - 手动命令调试仍可用
  - 生命周期系统自动驱动也可用
  - 两者通过 `source=MANUAL / VEGETATION_SYSTEM` 区分

## 当前原型范围

- 重点原型路径：`ecoflux:plains_to_forest`
- 当前原型植物：`minecraft:dandelion`
- 当前目标：验证单路径、单植物、单区块进度的完整运行链路，而不是正式平衡性

## 当前代码落点

- 主入口：`src/main/java/com/s/ecoflux/EcofluxMod.java`
- 区块附件：`src/main/java/com/s/ecoflux/attachment/SuccessionChunkData.java`
- 生命周期记录：`src/main/java/com/s/ecoflux/attachment/ActiveVegetationRecord.java`
- 原型核心逻辑：`src/main/java/com/s/ecoflux/prototype/PrototypeChunkController.java`
- 生命周期追踪器：`src/main/java/com/s/ecoflux/plant/VegetationTracker.java`
- 生命周期适配器接口：`src/main/java/com/s/ecoflux/plant/VegetationTypeAdapter.java`
- 当前植物适配器：`src/main/java/com/s/ecoflux/plant/SimplePlantAdapter.java`
- 当前树苗适配器：`src/main/java/com/s/ecoflux/plant/SaplingAdapter.java`
- 当前树结构适配器：`src/main/java/com/s/ecoflux/plant/TreeStructureAdapter.java`
- 生命周期 -> 视觉同步：`src/main/java/com/s/ecoflux/network/ModNetworking.java`
- 生命周期视觉载荷：`src/main/java/com/s/ecoflux/network/VegetationVisualChunkSyncPayload.java`
- 客户端视觉接口：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleAdapter.java`
- 客户端视觉运行时：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleClientRuntime.java`
- 世界重绘入口：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleWorldRenderer.java`
- 基础方块跳过渲染 Mixin：`src/main/java/com/s/ecoflux/mixin/client/BlockRenderDispatcherMixin.java`
- 调试命令：`src/main/java/com/s/ecoflux/init/ModCommands.java`
- 原型路径配置：`src/main/resources/data/ecoflux/succession_paths/plains_to_forest.json`

## 已实现能力

### 1. 区块初始化与原型队列

- 区块加载时会采样 biome / temperature / downfall 并匹配路径
- 匹配成功后初始化：
  - `currentBiome`
  - `targetBiome`
  - `previousBiome`
  - `activePathId`
  - `consumingValue`
  - `maxPlantCount`
  - `plantQueue`
- `plains_to_forest` 当前是单植物原型，队列为空时也会自动补回原型植物条目

### 2. 原型植物种植与生命周期记录

- 服务端会在区块内寻找合法放置点并放置 `minecraft:dandelion`
- 成功种下后：
  - 写入 `activePlants`
  - 同时调用 `VegetationTracker.trackAt(...)`
  - 生成 `ActiveVegetationRecord`
- `SimplePlantAdapter` 现在会在出生时就写入 born 阶段基础点数，而不是初始 0 点

### 3. 生命周期观察与视觉同步

- `VegetationTracker` 现在支持：
  - `trackAt(...)`
  - `observeTracked(...)`
  - `observeChunk(...)`
  - 构建区块级视觉同步快照
- 原型自动 tick 和 `/ecoflux prototype step` 现在都会执行：
  - `prune`
  - `spawn`
  - `observeChunk`
  - `evaluate`
- 服务端会在以下时机同步当前区块视觉快照：
  - 植被 track
  - 植被 observe / transform / remove
  - 玩家开始接收该 chunk
  - 玩家停止追踪该 chunk
- 客户端 visual runtime 现在支持两种来源：
  - `MANUAL`
  - `VEGETATION_SYSTEM`

### 4. 客户端视觉生命周期层

- 视觉层仍通过 world render fallback 路径保证 scale 一定可见
- 当前可验证：
  - `born -> mature` 的 scale 放大
  - `aging` 的颜色衰老
- 当前支持范围：
  - 地被：`minecraft:short_grass` / `minecraft:fern` / `minecraft:dead_bush`
  - 花：`#minecraft:small_flowers`
  - 树苗：`#minecraft:saplings`
  - fallback：任意被 track 的非空气方块
- 生命周期系统同步过来的阶段/进度会直接驱动 visual runtime 的 `externalState`

### 5. 进度评估与 biome 转化

- 当前区块进度仍来自 `activePlants`
- `totalPlantPoints >= consumingValue` 时按正步长推进
- 否则按负步长回退
- 达到 `>= 1.0` 时会真实把整个区块 biome 切到 `minecraft:forest`
- biome 转化完成后会清理 prototype 状态与生命周期视觉同步状态

## 当前命令

以下命令默认作用于“玩家脚下所在区块”：

### Prototype

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
  - 一次执行 `prune + spawn + observeChunk + evaluate`
- `/ecoflux prototype transition`
  - 强制当前区块立刻完成 biome 转化

### Prototype Auto

- `/ecoflux prototype auto on`
- `/ecoflux prototype auto off`
- `/ecoflux prototype auto status`

### Global Auto

- `/ecoflux auto on`
  - 一键开启“全套自动”：
    - 初始化当前区块
    - 接回 tracked chunk
    - 开启自动处理
    - 立刻执行一次 `step`
- `/ecoflux auto off`
- `/ecoflux auto status`

### Lifecycle

- `/ecoflux lifecycle inspect <x> <y> <z>`
  - 查看某个方块当前会被哪个生命周期 adapter 识别
- `/ecoflux lifecycle track <x> <y> <z>`
  - 手动把一个植被对象注册进生命周期系统
- `/ecoflux lifecycle observe <x> <y> <z>`
  - 手动观察一个 tracked 位置
- `/ecoflux lifecycle untrack <x> <y> <z>`
  - 手动移除一个 tracked 位置
- `/ecoflux lifecycle chunk`
  - 查看当前区块 tracked vegetation 概览
- `/ecoflux lifecycle observe_chunk`
  - 立刻观察当前区块内所有 tracked vegetation，并刷新客户端视觉状态
- `/ecoflux lifecycle sync_chunk`
  - 立刻把当前区块的生命周期视觉快照重发给客户端

### Visual

以下是客户端视觉调试命令，作用于指定坐标方块：

- `/ecoflux visual start <x> <y> <z>`
- `/ecoflux visual stop <x> <y> <z>`
- `/ecoflux visual inspect <x> <y> <z>`
- `/ecoflux visual stage <x> <y> <z> <born|growing|mature|aging>`
- `/ecoflux visual list`
- `/ecoflux visual clear`
- `/ecoflux visual help`
- `/ecoflux visual scale_override <value>`
- `/ecoflux visual scale_override clear`

## 当前验证结论

- 本地已验证 `./gradlew.bat compileJava` 成功
- 本地已验证 `./gradlew.bat build` 成功
- 生命周期系统已经不再只是服务端数据骨架，当前最小通路已能直接驱动客户端视觉层
- 命令调试入口和自动入口现在都能走到同一套生命周期 -> 视觉同步链路

## 已知现状与限制

- 当前仍然是原型实现，不是正式玩法版本
- 当前只重点保证 `plains_to_forest` 的最小闭环
- 当前区块进度结算仍然使用 `activePlants` 点数，**还没有切到 `vegetationRecords` 的积分闭环**
- 玩家手动种下的植物还没有自动计入 `activePlants`
- 当前视觉层负责表现，不负责真实出生/死亡来源判定
- 对任意 tracked 方块都能保证 scale 效果，但颜色衰老效果只会在可着色植物上更明显
- 目前的自动模式仍是原型级调度，还没有做更细的负载控制和更广泛的数据同步设计
- 当前 biome 转化仍然是“整块直接切换”，边界混合尚未开始做

## 建议的下一步

- 优先把 `vegetationRecords` 积分接入区块进度结算，补完真正的生命周期 -> 演替积分闭环
- 再把玩家放置 / 原版自然生长 / 消失事件正式接到 `VegetationTracker`
- 保留当前视觉生命周期抽象层，不要把它和演替积分逻辑揉在一起
- 在自动模式稳定后，再扩回多植物队列与权重抽取
- 然后再考虑更完整的网络同步、边界处理和兼容层
