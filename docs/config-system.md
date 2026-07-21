# 配置系统

> 最后更新: 2026-06-16

配置系统负责加载、缓存和匹配演替路径定义、群系植物规则和植物定义。所有演替规则由 JSON 数据驱动，不需要硬编码。

## 相关文档

修改配置系统前必读：[architecture.md](architecture.md) · [succession-system.md](succession-system.md)

## 核心组件

| 类 | 职责 |
|----|------|
| `AbstractConfigRegistry<K,T>` | 基类：线程安全的 `replaceAll(entries, keyExtractor)` + `get(key)` + `getAll()`（统一 ConcurrentHashMap + volatile 策略） |
| `AbstractJsonConfigLoader<T>` | 基类：统一 GSON 实例、`SimpleJsonResourceReloadListener` 构造、`apply()` 骨架（stream → sort → schema_version 校验 → parseFile → onLoadComplete） |
| `SuccessionConfigLoader` | 继承 `AbstractJsonConfigLoader`，加载演替路径定义，覆盖 `apply()` 实现 path_id 重复检测 |
| `SuccessionConfigRegistry` | 继承 `AbstractConfigRegistry<ResourceLocation, SuccessionPathDefinition>`，额外维护 sourceBiome 多索引和 `allPaths` 排序列表，提供 `findBestMatch()` |
| `SuccessionPathDefinition` | Record: 演替路径定义（pathId, priority, source/target/fallback biomes, climate, step sizes） |
| `BiomeRules` | Record: 群系植物生态规则（biomeId, maxPlantCount, consuming, queueFillFactor, plants） |
| `BiomeRulesRegistry` | 继承 `AbstractConfigRegistry<ResourceLocation, BiomeRules>`，线程安全静态方法 |
| `BiomeRulesLoader` | 继承 `AbstractJsonConfigLoader`，加载 `biome_rules/` 目录 |
| `ClimateCondition` | Record: temperature/downfall 范围匹配 |
| `PlantDefinition` | Record: 中心植物注册表条目 (plant_id, pointValue, maxAgeTicks, spawnRules) |
| `PlantRegistry` | 继承 `AbstractConfigRegistry<ResourceLocation, PlantDefinition>`，实例单例（非静态） |
| `PlantRegistryLoader` | 继承 `AbstractJsonConfigLoader`，加载 `plant_definitions/` 目录，一个文件可含多个植物定义 |
| `PathPlantEntry` | Record: 植物引用 (plant_id + weight)，被 BiomeRules 和旧路径共用 |
| `PlantSpawnRules` | Record: 生成规则 (requireSky, maxLocalDensity, allowedBaseBlocks) |
| `FloatRange` / `IntRange` | Record: 范围工具类型（min/max） |
| `EcofluxServerConfig` | 服务端 NeoForge 配置，包含全局 `evaluation_interval_ticks`、生成间隔、性能间隔等 |

以下 record 同时存在于 `com.cp.ecoflux.api.config` 包，供外部 mod 引用（`SuccessionPathDefinition`、`PlantDefinition`、`PlantSpawnRules`、`ClimateCondition`、`FloatRange`、`IntRange`）。
| ~~`ChunkRules`~~ | **已删除 (2026-06-14)**。内容拆分到 `BiomeRules`、`SuccessionPathDefinition` 和 `EcofluxServerConfig` |

## JSON 文件位置

```
src/main/resources/data/ecoflux/
├── succession_paths/              # 演替路径定义 (20 个文件)
│   ├── plains_to_forest.json
│   ├── plains_to_meadow.json
│   └── ...
├── biome_rules/minecraft/         # 群系植物规则 (64 个文件)
│   ├── plains.json
│   ├── forest.json
│   └── ...
├── plant_definitions/
│   └── plants.json                # 中心植物注册表 (所有植物的完整定义)
├── neoforge/biome_modifier/
│   ├── cancel_vanilla_trees.json
│   └── add_ecoflux_trees.json
└── tags/blocks/
    ├── simple_vegetation.json
    ├── grass_cover.json
    ├── uses_foliage_tint.json
    └── uses_grass_tint.json
```

## SuccessionPathDefinition JSON Schema (v1, 2026-06-14 简化)

```json
{
  "schema_version": 1,
  "path_id": "ecoflux:plains_to_forest",
  "priority": 10,
  "source_biomes": ["minecraft:plains", "minecraft:sunflower_plains"],
  "target_biome": "minecraft:forest",
  "fallback_biome": "minecraft:plains",
  "climate": {
    "temperature": { "min": 0.6, "max": 0.95 },
    "downfall": { "min": 0.4, "max": 0.9 }
  },
  "chunk_rules": {
    "positive_progress_step": 0.25,
    "negative_progress_step": 0.25
  }
}
```

**重要变更 (2026-06-14):** `plants[]` 移除，植物生态配置移至 `biome_rules/`。`chunk_rules` 简化为仅含步长。`consuming`、`max_plant_count`、`queue_fill_factor` 移至 BiomeRules。`evaluation_interval_days` 已移除，评估间隔现在由 `EcofluxServerConfig.evaluationIntervalTicks()` 全局控制。

### 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `schema_version` | int | 是 | 当前为 `1` |
| `path_id` | string | 是 | 唯一标识符，跨文件不能重复 |
| `priority` | int | 否(默认0) | 匹配优先级，值越大越优先 |
| `source_biomes` | string[] | 是 | 源群系列表，如 `["minecraft:plains"]` |
| `target_biome` | string | 是 | 目标群系，如 `"minecraft:forest"` |
| `fallback_biome` | string | 否 | 退化目标群系，默认回到源群系 |
| `climate` | object | 是 | 气候条件 |
| `chunk_rules` | object | 是 | 路径步长规则（仅含 step 字段） |

### climate 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `temperature` | `{min, max}` | 温度范围 [0.0, 2.0] |
| `downfall` | `{min, max}` | 湿度范围 [0.0, 1.0] |

### chunk_rules 字段（简化后）

| 字段 | 类型 | 说明 |
|------|------|------|
| `positive_progress_step` | double | 正向演替每步进度增量（默认 0.5） |
| `negative_progress_step` | double | 负向退化每步进度减量（默认 0.25） |

## BiomeRules JSON Schema (v1)

```json
{
  "schema_version": 1,
  "biome_id": "minecraft:plains",
  "min_plant_count": 12,
  "max_plant_count": 28,
  "consuming": 12,
  "queue_fill_factor": 2.0,
  "plants": [
    { "plant_id": "minecraft:short_grass", "weight": 10 },
    { "plant_id": "minecraft:dandelion", "weight": 7 }
  ]
}
```

群系植物规则文件位于 `data/ecoflux/biome_rules/<namespace>/<biome_path>.json`。

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `schema_version` | int | 是 | 当前为 `1` |
| `biome_id` | string | 是 | 群系 ResourceLocation，如 `"minecraft:plains"` |
| `min_plant_count` | int | 否(默认=`max_plant_count`) | 区块植物数下限。未指定时等于 max_plant_count（无随机性） |
| `max_plant_count` | int | 是 | 区块植物数上限。队列容量 = `max_plant_count × queue_fill_factor` |
| `consuming` | int | 是 | 维持当前群系所需的植物点数 |
| `queue_fill_factor` | double | 是 | 队列容量倍数 |
| `plants` | array | 是 | 植物列表（仅含 plant_id + weight） |

### 植物数正态分布采样

区块初始化时，每个区块的植物数上限不是固定值，而是从正态分布中随机采样：

- 均值 μ = (min_plant_count + max_plant_count) / 2
- 标准差 σ = (max_plant_count - min_plant_count) / 6（使 [min, max] 覆盖 ±3σ，即 99.7%）
- 结果 clamp 到 [min, max]

这模拟了原版世界生成中每个区块植物数量不恒定的特性。采样值存储在 `SuccessionChunkData.maxPlantCount` 中，决定该区块允许的最大植物数。

### 植物采样器 (`/ecoflux sample`)

用于采集原版世界生成数据来校准 BiomeRules 配置：

1. 以玩家所在区块为中心，BFS 搜索半径内相同群系的相连区块
2. 强制加载未加载的区块
3. 扫描每个区块内所有匹配 `VegetationTypeAdapter` 的植物方块
4. 输出报告：采样区块数、每区块植物数分布、每种植物方块的总数和百分比
5. `/ecoflux sample <radius> apply` — 自动生成 `BiomeRules` JSON 到 `{server}/ecoflux/sampled_rules/`
6. `/ecoflux sample batchall [radius]` — 遍历所有 `minecraft:` 群系，批量生成采样数据

用法：`/ecoflux sample`（默认半径 5）或 `/ecoflux sample <radius>`（1-20）

### plants[] 字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `plant_id` | string | 是 | 植物方块 ID，对应 `PlantRegistry` 中的定义 |
| `weight` | int | 是 | 队列随机权重 |

### PlantDefinition JSON 格式 (plant_definitions/plants.json)

植物完整定义集中在中心注册表中：

```json
{
  "schema_version": 1,
  "plants": [
    {
      "plant_id": "minecraft:dandelion",
      "point_value": 2,
      "max_age_ticks": 72000,
      "spawn_rules": {
        "require_sky": true,
        "max_local_density": 6,
        "allowed_base_blocks": [
          "minecraft:grass_block",
          "minecraft:dirt"
        ]
      }
    }
  ]
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `plant_id` | string | 是 | 方块 ID |
| `point_value` | int | 是 | 植物提供的演替点数 |
| `max_age_ticks` | long | 是 | 植物最大寿命（游戏 tick） |
| `spawn_rules` | object | 是 | 生成规则 |

### spawn_rules 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `require_sky` | bool | 是否需要天空光照 |
| `max_local_density` | int | 附近同类型植物密度上限 |
| `allowed_base_blocks` | string[] | 允许种植的基底方块 |

## 加载流程

```
/reload 或首次加载
  │
  ├─→ PlantRegistryLoader (SimpleJsonResourceReloadListener)
  │     ├─ 扫描 data/ecoflux/plant_definitions/*.json
  │     ├─ Gson 反序列化 → List<PlantDefinition>
  │     └─→ PlantRegistry.reload(definitions)
  │           └─ 构建 ConcurrentHashMap 缓存
  │
  ├─→ SuccessionConfigLoader (SimpleJsonResourceReloadListener)
  │     ├─ 扫描 data/ecoflux/succession_paths/*.json
  │     ├─ Gson 反序列化 → List<SuccessionPathDefinition>
  │     ├─ 校验 path_id 唯一性
  │     └─→ SuccessionConfigRegistry.reload(paths)
  │           └─ 构建缓存（按 source_biome 索引）
  │
  └─→ BiomeRulesLoader (SimpleJsonResourceReloadListener)
        ├─ 扫描 data/ecoflux/biome_rules/**/*.json
        ├─ Gson 反序列化 → List<BiomeRules>
        └─→ BiomeRulesRegistry.replace(rules)
              └─ 构建缓存（按 biomeId 索引）
```

### 区块初始化时的配置查询流程

```
Chunk 加载
  │
  ├─→ ChunkSamplingHelper.sampleChunkCenterBiome() → 当前群系
  ├─→ ChunkSamplingHelper.sampleChunkClimate() → 温度/湿度
  │
  ├─→ SuccessionConfigRegistry.findBestMatch(biome, temp, downfall) → 演替路径
  │     └─ 提供: targetBiome, fallbackBiome, positiveProgressStep, negativeProgressStep
  │
  └─→ BiomeRulesRegistry.getRules(biomeId) → 群系规则
        └─ 提供: maxPlantCount, consuming, queueFillFactor, plants[]
```

## 路径匹配算法

`SuccessionConfigRegistry.findBestMatch(biome, temperature, downfall)`：

1. 筛选 `source_biomes` 包含当前群系的所有路径
2. 在候选中过滤气候条件匹配的（temperature/downfall 在范围内）
3. 按 `priority` 降序排列
4. 返回第一个匹配的路径

如果没有找到匹配路径，或者没有对应的 BiomeRules，chunk 不参与演替。

## 辅助配置

### EcofluxServerConfig
服务端 TOML 配置（`ecoflux-server.toml`），包含：
- 渐进式树木/植物生长开关（`gradual_tree_growth`、`gradual_plant_growth`）
- 植物生成开关（`disable_plant_spawning`）
- 全局评估间隔 `evaluation_interval_ticks`（默认 24000 = 1 游戏日）
- 生成间隔范围 `spawn_interval_min_ticks` / `spawn_interval_max_ticks`（默认 600 / 1800）
- 性能清理间隔 `prune_interval_ticks`（默认 120 tick）
- 性能观察间隔 `observe_interval_ticks`（默认 120 tick）
- 视觉系统开关 `enable_visual_system`（默认 false）

### VisualLifecycleClientConfig
客户端 TOML 配置（`ecoflux-client.toml`），控制视觉生命周期渲染开关和参数。

### SuccessionSpeedConfig
全局速度倍率配置，供调试/原型模式使用。

## 已知限制

- 配置文件依赖 `/reload` 或重启才能更新（运行时无法动态添加）

## 如何添加新的演替路径

1. 在 `src/main/resources/data/ecoflux/succession_paths/` 创建新 JSON 文件
2. 填写必需字段：`schema_version: 1`、`path_id`（唯一）、`source_biomes`、`target_biome`、`chunk_rules`（`positive_progress_step` + `negative_progress_step`）
3. 确保 `source_biomes` 中的群系在 `biome_rules/` 中有对应的群系规则文件
4. 可选：添加 `climate` 限制、`fallback_biome` 退化目标、`priority` 优先级
5. 运行 `/reload` 或重启游戏
6. 运行 `/reload` 后用 `/ecoflux lifecycle inspect <x> <y> <z>` 验证路径被正确加载

## 如何添加新群系的植物规则

1. 在 `src/main/resources/data/ecoflux/biome_rules/<namespace>/` 创建新 JSON 文件（文件名通常为群系路径的最后一段，如 `plains.json`）
2. 填写必需字段：`schema_version: 1`、`biome_id`、`max_plant_count`、`consuming`、`queue_fill_factor`、`plants[]`
3. 确保 `plants[]` 中引用的 `plant_id` 在 `plant_definitions/plants.json` 中有对应定义
4. 如果该群系有对应的演替路径，无需修改路径文件——路径会通过 `source_biomes` 自动匹配
5. 或使用 `/ecoflux sample <radius> apply` 自动生成采样数据
6. 运行 `/reload` 或重启游戏

## 如何添加新植物

1. 在 `plant_definitions/plants.json` 的 `plants` 数组中添加条目，填写 `plant_id`、`point_value`、`max_age_ticks`、`spawn_rules`
2. 在需要使用该植物的群系规则 JSON 的 `plants[]` 中添加 `{"plant_id": "minecraft:xxx", "weight": N}` 引用
3. 确保有对应的 `VegetationTypeAdapter` 能匹配该植物方块
4. 运行 `/reload` 或重启游戏
