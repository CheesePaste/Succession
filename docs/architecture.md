# 项目架构草案

本文档基于 `README.md` 提取面向实现的架构，不依赖其他 Git 分支。

## 1. 架构目标

把 README 中的玩法拆成四层：

1. 配置层：加载演替路径、植物权重、环境条件和 consuming 规则。
2. 数据层：在区块上持久化演替进度、植物队列和活跃植物状态。
3. 运行层：处理随机刻、低频评估、植物增减和群系替换。
4. 兼容层：负责网络同步和可选的 `Dynamic Trees` 联动。

## 2. 推荐包结构

以下结构按统一命名 `Ecoflux` 设计，建议后续代码目录同步迁移到 `com.s.ecoflux`。

```text
src/main/java/com/s/ecoflux/
  EcofluxMod.java                   模组主入口
  init/
    ModAttachments.java             Data Attachments 注册
    ModEvents.java                  通用事件绑定入口
    ModRegistries.java              需要集中注册的对象
  attachment/
    SuccessionChunkData.java        区块演替核心状态
    ActivePlantRecord.java          已追踪植物记录
    PlantQueueEntry.java            待种植植物条目
  config/
    SuccessionPathDefinition.java   单条演替路径定义
    PlantDefinition.java            植物配置定义
    ClimateCondition.java           温度/湿度/群系匹配条件
    SuccessionConfigLoader.java     JSON 加载与校验
    SuccessionConfigRegistry.java   已加载配置缓存
  succession/
    SuccessionService.java          演替主流程编排
    SuccessionEvaluator.java        积分、consuming、进度结算
    SuccessionTargetResolver.java   目标群系解析
    BiomeTransitionService.java     群系替换调用封装
  plant/
    PlantTracker.java               活跃植物登记/注销
    PlantLifecycleHooks.java        破坏、凋零、生成监听
    PlantSpawner.java               从队列尝试种植
    PlantClassifier.java            判断哪些方块属于可管理植物
  world/
    ChunkTickCoordinator.java       随机刻和低频评估调度
    ChunkSamplingHelper.java        区块中心/边界采样工具
  network/
    ChunkSyncPayload.java           客户端显示所需同步包
    NetworkHandler.java             可选同步注册
  compat/
    DynamicTreesCompat.java         动态树兼容入口
    CompatManager.java              兼容能力探测
  util/
    WeightedPicker.java             权重随机工具
    BlockPosCodec.java              坐标序列化辅助
```

## 3. 资源与数据布局

建议的资源布局如下：

```text
src/main/resources/
  assets/ecoflux/
    lang/
      en_us.json
      zh_cn.json
  data/ecoflux/
    succession_paths/
      *.json
```

注意：

- `README.md` 的数据路径与当前统一命名一致，后续资源应向 `ecoflux` 收敛。
- 当前仓库仍保留 `succession` 遗留目录，重构时需要整体替换。
- 代码里不要把路径字符串散落硬编码；统一通过常量或配置注册表提供命名空间。

## 4. 核心数据对象

### 4.1 `SuccessionChunkData`

区块级核心状态建议至少包含：

- `currentBiome`
- `targetBiome`
- `previousBiome`
- `progress`
- `consumingValue`
- `maxPlantCount`
- `currentPlantCount`
- `queueVersion`，用于配置刷新后判断队列是否需要重建
- `Queue<PlantQueueEntry> plantQueue`
- `Map<BlockPos, ActivePlantRecord> activePlants`
- `lastEvaluationGameTime`

### 4.2 `PlantQueueEntry`

建议字段：

- `plantId`
- `pointValue`
- `weight`
- `maxAge`
- `spawnRulesRef`

### 4.3 `ActivePlantRecord`

建议字段：

- `plantId`
- `pointValue`
- `spawnPos`
- `birthGameTime`
- `expireGameTime`
- `sourceBiome`

## 5. 运行时主流程

### 5.1 区块初始化

1. 区块加载时附加 `SuccessionChunkData`。
2. 如果数据为空，则根据当前群系匹配演替路径。
3. 初始化 consuming、植物上限和植物队列。

### 5.2 随机刻 / 常规驱动

1. 调度器判定当前区块是否需要处理。
2. 若 `currentPlantCount < maxPlantCount` 且队列非空，则尝试种植。
3. 种植成功后把结果加入 `activePlants`，并更新数量。
4. 种植失败时按策略决定重试、丢弃或延后。

### 5.3 生命周期维护

1. 监听植物自然凋零、被破坏、替换或失效。
2. 从 `activePlants` 移除对应记录。
3. 重新统计或增量调整植物总积分。

### 5.4 低频评估

1. 每隔 3~7 游戏日对区块做一次评估。
2. 计算 `totalPlantPoints` 与 `consumingValue` 的差值。
3. 差值为正则增加进度，反之减少进度。
4. 当 `progress >= 1.0` 时执行正向演替；当 `progress <= -1.0` 时回退到上一阶段。

### 5.5 群系替换

1. 解析目标群系。
2. 调用群系替换服务执行真实变更。
3. 重置区块演替状态、重新生成队列，并视需要对边界表层做混合缓解。

## 6. 配置层设计建议

单条演替路径建议描述这些信息：

- 源群系
- 目标群系
- 气候条件（温度、湿度范围，可选额外过滤）
- consuming 数值
- 最大植物容量
- 植物列表及权重
- 可回退目标

建议把“路径匹配”和“植物定义复用”分开：

- `succession_paths/*.json` 负责描述从什么群系到什么群系。
- 如果后续配置变多，可以再拆出 `plant_sets/*.json` 供多条路径复用。

## 7. 性能策略

- 区块评估严格低频化，不在每 tick 结算。
- 尽量使用增量更新，避免反复全量扫描区块内植物。
- 区块未加载时不做额外工作。
- 把“边界混合”放在演替完成瞬间执行，而不是持续执行。
- 兼容层探测只在启动或资源重载时做一次。

## 8. 当前最值得先落地的最小闭环

第一阶段不要一上来实现全部能力，建议先打通以下闭环：

1. 区块附件可读写。
2. 能从 JSON 解析一条演替路径。
3. 能把 1 种植物写入队列并在区块里生成。
4. 能统计点数并推动进度变化。
5. 能在阈值达到后触发一次真实群系替换。

有了这个最小闭环，再补植物分类、更多路径、同步和外部联动。
