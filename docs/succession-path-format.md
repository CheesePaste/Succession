# Succession Path JSON v1

本文档定义 `data/ecoflux/succession_paths/` 下第一版 JSON 结构，用于后续实现数据驱动的生态演替路径加载器。

## 设计目标

- 对齐 `README.md` 中“JSON 配置驱动”的要求。
- 先覆盖最小闭环：来源群系、目标群系、气候条件、区块规则、植物列表。
- 支持同一来源群系存在多条分支路径，并通过 `priority` 控制匹配优先级。
- 只基于当前分支文档与现状设计，不参考其他 Git 分支。

## 根对象字段

```json
{
  "schema_version": 1,
  "path_id": "ecoflux:plains_to_forest",
  "priority": 10,
  "source_biomes": [
    "minecraft:plains"
  ],
  "target_biome": "minecraft:forest",
  "fallback_biome": "minecraft:plains",
  "climate": {
    "temperature": {
      "min": 0.6,
      "max": 0.95
    },
    "downfall": {
      "min": 0.45,
      "max": 0.9
    }
  },
  "chunk_rules": {
    "consuming": 12,
    "max_plant_count": 24,
    "queue_fill_factor": 2.0,
    "evaluation_interval_days": {
      "min": 3,
      "max": 5
    }
  },
  "plants": []
}
```

字段说明：

- `schema_version`：JSON 结构版本，第一版固定为 `1`
- `path_id`：路径唯一标识，便于日志和调试
- `priority`：匹配优先级，数值越大越优先
- `source_biomes`：允许触发这条路径的来源群系列表
- `target_biome`：进度满时切换到的目标群系
- `fallback_biome`：进度回退时的候选群系，暂定为可选字段
- `climate`：气候匹配条件，当前只包含温度与湿度（`downfall`）
- `chunk_rules`：区块级演替参数
- `plants`：该路径可生成的植物池

## `climate` 对象

```json
{
  "temperature": {
    "min": 0.6,
    "max": 0.95
  },
  "downfall": {
    "min": 0.45,
    "max": 0.9
  }
}
```

- `temperature.min` / `temperature.max`：允许的温度范围
- `downfall.min` / `downfall.max`：允许的湿度范围

当前版本先使用闭区间数值匹配；后续如果需要群系标签、海拔或维度过滤，再扩展字段。

## `chunk_rules` 对象

```json
{
  "consuming": 12,
  "max_plant_count": 24,
  "queue_fill_factor": 2.0,
  "evaluation_interval_days": {
    "min": 3,
    "max": 5
  }
}
```

- `consuming`：区块维持当前阶段所需的植物积分消耗值
- `max_plant_count`：区块内可追踪植物上限
- `queue_fill_factor`：预生成队列长度倍率，通常队列长度约等于 `max_plant_count * queue_fill_factor`
- `evaluation_interval_days`：低频评估区间，后续运行时在这个范围内取随机值

## `plants` 数组元素

```json
{
  "plant_id": "minecraft:short_grass",
  "category": "ground_cover",
  "weight": 8,
  "point_value": 1,
  "max_age_ticks": 48000,
  "spawn_rules": {
    "placement": "surface",
    "require_sky": true,
    "max_local_density": 8,
    "allowed_base_blocks": [
      "minecraft:grass_block",
      "minecraft:dirt"
    ]
  }
}
```

字段说明：

- `plant_id`：植物方块或逻辑植物标识
- `category`：便于后续扩展分类逻辑，如 `tree`、`flower`、`mushroom`、`ground_cover`
- `weight`：进入队列时的权重
- `point_value`：单株植物提供的积分
- `max_age_ticks`：寿命上限，供生命周期系统使用
- `spawn_rules`：种植位置和约束条件

## 分支匹配约定

- 同一来源群系可以配置多条路径。
- 加载器后续应先筛出所有 `source_biomes` 命中的路径，再按气候条件过滤。
- 若多条路径同时匹配，则按 `priority` 从高到低选择。
- 若 `priority` 相同，建议在加载阶段直接记录警告，避免行为不明确。

## 当前示例文件

- `src/main/resources/data/ecoflux/succession_paths/plains_to_forest.json`
- `src/main/resources/data/ecoflux/succession_paths/forest_to_birch_forest.json`
- `src/main/resources/data/ecoflux/succession_paths/forest_to_flower_forest.json`

其中最后两条故意共享同一来源群系，用于展示未来分支匹配与优先级机制。
