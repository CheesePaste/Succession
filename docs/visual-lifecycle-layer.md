# Visual Lifecycle Layer

本文档定义当前 Ecoflux “视觉生命周期层（Visual Lifecycle Layer）”的接口边界、核心抽象、运行流程与扩展方式。

这层的目标不是决定植物什么时候真实出生、成长、死亡，而是把“某个世界对象当前处于哪个视觉阶段”稳定地转成：

- 模型 scale 变化
- 衰老 tint 变化
- 客户端可调试、可复用的视觉表现

它是后续更大“植物生命系统”的一个下层渲染抽象，而不是那个系统本身。

## 当前定位

- 这是一个 **client-side 的视觉抽象层**。
- 它只关心“怎么画”，不关心“为什么现在该画成这样”。
- 它允许上层系统把真实生命周期结果喂进来。
- 它也保留手动命令调试入口，便于纯视觉排查。
- 当前已支持两条输入源并存：
  - `MANUAL`
  - `VEGETATION_SYSTEM`

换句话说：

- 上层生命系统负责：这个植物现在是不是 `mature / aging`
- 视觉层负责：`mature / aging` 在画面上到底长什么样

## 设计目标

当前视觉层需要满足：

- 支持统一的阶段化视觉表达
- 支持不同植物类型复用同一套接口
- 支持命令强制调试
- 支持客户端配置调 scale
- 不强耦合当前演替原型
- 可以被上层生命系统直接驱动

## 明确边界

### 这层负责什么

- 维护客户端 tracked visual instances
- 为某个 `BlockState` 找到视觉适配器
- 根据本地时间或外部同步状态，计算当前视觉 render state
- 将 render state 转为：
  - `scale`
  - `tintedColor`
  - `stage`
  - `stageProgress`
- 在客户端渲染阶段绘制缩放后的 tracked block

### 这层不负责什么

- 不负责真实出生判定
- 不负责真实死亡判定
- 不负责植物积分
- 不负责演替进度
- 不负责服务端生命周期计算

所以这层不是：

- `VegetationTracker`
- `PlantLifecycleSystem`
- `PrototypeChunkController`

它只是这些系统未来和现在都可以调用的视觉表现层。

## 核心类型

### 1. `VisualLifecycleStage`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleStage.java`

当前视觉阶段：

- `BORN`
- `GROWING`
- `MATURE`
- `AGING`

这是一套视觉阶段，不等于完整逻辑生命周期全集。

### 2. `VisualLifecycleProfile`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleProfile.java`

它描述一个视觉生命周期模板，包括：

- 各阶段时长
- born / mature / aging 的 scale 锚点
- aging 的 hue / saturation / brightness 偏移参数

这是“视觉数据模板”，不包含世界实例状态。

### 3. `VisualLifecycleExternalState`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleExternalState.java`

这是现在已经落地的外部驱动状态：

- `VisualLifecycleStage stage`
- `float stageProgress`

它表示“这个实例的阶段/进度由外部系统权威提供”。

### 4. `VisualLifecycleTrackingSource`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleTrackingSource.java`

当前来源分为：

- `MANUAL`
- `VEGETATION_SYSTEM`

这个区分很重要，因为当前 runtime 既要保留命令调试，也要承接服务端生命周期同步。

### 5. `VisualLifecycleInstance`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleInstance.java`

它表示一个被客户端 tracked 的视觉实例，核心字段现在包括：

- `adapterId`
- `blockId`
- `pos`
- `startGameTime`
- `profile`
- `forcedStage`
- `externalState`
- `source`

职责：

- 标识由哪个 adapter 管
- 记录对应哪个 block
- 记录位置
- 记录视觉生命周期起始时间
- 支持命令强制阶段覆盖
- 支持服务端生命周期系统注入外部阶段/进度

注意：

- 当 `externalState == null` 时，实例退回到本地时间驱动
- 当 `externalState != null` 时，视觉层直接使用外部同步阶段/进度

### 6. `VisualLifecycleRenderState`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleRenderState.java`

它是渲染层真正消费的结果对象：

- `stage`
- `stageProgress`
- `scale`
- `tintedColor`

这是视觉层里最重要的“渲染协议对象”。

### 7. `VisualLifecycleAdapter`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleAdapter.java`

这是视觉层最核心的扩展接口。

当前职责：

- `matches(BlockState state)`：决定某个方块归哪个视觉适配器管理
- `createProfile(BlockState state)`：生成视觉 profile
- `resolveState(...)`：根据实例、本地时间、外部状态和基础颜色计算最终 render state

当前默认实现已经内置：

- 本地时间驱动阶段推导
- 外部阶段/进度优先覆盖
- scale 插值
- aging tint 插值

这意味着：

- 新适配器通常只要提供 `matches + createProfile`
- 如果某类对象需要特殊视觉曲线，也可以 override `resolveState`

### 8. `VisualLifecycleRegistry`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleRegistry.java`

职责：

- 管理所有视觉 adapter
- 提供 `find(BlockState)` 做适配器匹配
- 提供帮助文本和颜色注册辅助信息

当前已注册：

- `GrassVisualLifecycleAdapter`
- `FlowerVisualLifecycleAdapter`
- `SaplingVisualLifecycleAdapter`
- `GenericVisualLifecycleAdapter`

其中 `GenericVisualLifecycleAdapter` 是 fallback，保证任意被 track 的非空气方块至少都有 scale 效果。

### 9. `VisualLifecycleClientRuntime`

文件：`src/main/java/com/s/ecoflux/client/visual/VisualLifecycleClientRuntime.java`

这是视觉层的客户端运行时核心。

它管理：

- 当前所有 tracked instances
- 当前维度内的实例查询
- 命令入口调用
- 生命周期系统同步入口
- 渲染重建触发
- 手动 world render pass 状态

主要接口：

- `start(BlockPos pos)`
- `stop(BlockPos pos)`
- `clear()`
- `inspect(BlockPos pos)`
- `list()`
- `forceStage(BlockPos pos, VisualLifecycleStage stage)`
- `syncVegetationChunk(ResourceLocation dimensionId, ChunkPos chunkPos, List<...> entries)`
- `getRenderState(BlockPos pos, BlockState state)`
- `adjustTint(BlockState state, BlockPos pos, int baseColor)`

当前内部数据结构：

- `Map<String, VisualLifecycleInstance> trackedInstances`
- `Map<Long, VisualLifecycleInstance> trackedInstancesByPos`

这样做的原因：

- 命令和列表更适合按“维度 + 位置”管理
- 渲染路径更适合按 `BlockPos.asLong()` 快速查询

## 当前渲染实现

### 1. tint 路径

文件：`src/main/java/com/s/ecoflux/client/visual/ModClientVisualLifecycle.java`

通过 `RegisterColorHandlersEvent.Block` 注册 block color handler。

流程：

1. 原版或 biome 提供基础颜色
2. `VisualLifecycleClientRuntime.adjustTint(...)`
3. 若该位置被 tracked，则返回 aging 后的 tint

### 2. scale 路径

相关文件：

- `src/main/java/com/s/ecoflux/mixin/client/BlockRenderDispatcherMixin.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleWorldRenderer.java`

当前策略：

1. 对 `scale != 1` 的 tracked block，原始基础 block render pass 会被跳过
2. 在 `RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES` 阶段，按 tracked 列表重新绘制缩放后的 block model
3. 这份重绘使用 `renderBatched(...)`，保留 world + pos 语义，确保 tint 跟 world 渲染路径一致

## 当前适配器实现

### `GrassVisualLifecycleAdapter`

匹配：

- `minecraft:short_grass`
- `minecraft:fern`
- `minecraft:dead_bush`

### `FlowerVisualLifecycleAdapter`

匹配：

- `#minecraft:small_flowers`

### `SaplingVisualLifecycleAdapter`

匹配：

- `#minecraft:saplings`

### `GenericVisualLifecycleAdapter`

匹配：

- 任意非空气方块

## 调试入口

文件：`src/main/java/com/s/ecoflux/client/visual/ModClientVisualLifecycle.java`

当前命令：

- `/ecoflux visual start <x> <y> <z>`
- `/ecoflux visual stop <x> <y> <z>`
- `/ecoflux visual inspect <x> <y> <z>`
- `/ecoflux visual stage <x> <y> <z> <born|growing|mature|aging>`
- `/ecoflux visual list`
- `/ecoflux visual clear`
- `/ecoflux visual scale_override <value>`
- `/ecoflux visual scale_override clear`

这套命令的意义：

- 在没有服务端完整链路时，单独验证视觉层
- 在服务端链路已接通后，继续作为纯视觉排查工具

## 客户端配置接口

文件：`src/main/java/com/s/ecoflux/config/VisualLifecycleClientConfig.java`

配置文件：`config/ecoflux-client.toml`

当前配置项：

- `visual_lifecycle.use_configured_stage_scales`
- `visual_lifecycle.born_scale`
- `visual_lifecycle.growing_start_scale`
- `visual_lifecycle.mature_scale`
- `visual_lifecycle.aging_scale`

另外还有运行时调试覆盖：

- `debugUniformScaleOverride`

## 当前运行流程

### 手动调试路径

1. 玩家执行 `/ecoflux visual start x y z`
2. runtime 根据 `BlockState` 找到 `VisualLifecycleAdapter`
3. adapter 生成 `VisualLifecycleProfile`
4. runtime 保存 `VisualLifecycleInstance`
5. 渲染层持续消费 `VisualLifecycleRenderState`

### 生命周期系统自动驱动路径

1. 服务端 `VegetationTracker.observeChunk(...)` 更新区块内生命周期记录
2. adapter 把逻辑阶段映射成 `VegetationVisualState`
3. `VegetationVisualChunkSyncPayload` 把当前 chunk 的视觉快照发给客户端
4. `VisualLifecycleClientRuntime.syncVegetationChunk(...)` 用 `externalState` upsert 当前实例
5. 渲染层继续只消费 `VisualLifecycleRenderState`，不需要知道服务端生命周期细节

## 当前决议

- 视觉生命周期层是一个独立的 client-side 抽象层
- 它的核心扩展点是 `VisualLifecycleAdapter`
- 它的核心输出协议是 `VisualLifecycleRenderState`
- 它现在已经支持被服务端生命周期系统直接驱动
- 它仍然不拥有真实生命逻辑，只拥有视觉表现逻辑

## 当前相关文件

- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleStage.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleProfile.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleExternalState.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleTrackingSource.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleInstance.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleRenderState.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleAdapter.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleRegistry.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleClientRuntime.java`
- `src/main/java/com/s/ecoflux/client/visual/VisualLifecycleWorldRenderer.java`
- `src/main/java/com/s/ecoflux/client/visual/ModClientVisualLifecycle.java`
- `src/main/java/com/s/ecoflux/config/VisualLifecycleClientConfig.java`
- `src/main/java/com/s/ecoflux/mixin/client/BlockRenderDispatcherMixin.java`
- `src/main/java/com/s/ecoflux/network/ModNetworking.java`
- `src/main/java/com/s/ecoflux/network/VegetationVisualChunkSyncPayload.java`
