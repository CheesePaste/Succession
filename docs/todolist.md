# TODO List

> 最后更新: 2026-06-16
建议：
1.适配HT's TreeChop
重新生长得树无法被HT's TreeChop识别成完整树，导致树木需要自己慢慢搭上去然后砍下来
2.适配其他模组的树或添加树白名单？如超多生物群系Biomes O' Plenty
3.树木演化多样性，现在生成的树木基本都一个样
以下待办基于当前代码实现状态整理。

## 近期完成 (2026-06-14/15)

- [x] 空间定殖树算法：9 种树种 (1x1 + 2x2) + 世界生成集成
- [x] BiomeModifier 树替换系统（CancelVanillaTrees + EcofluxTreeFeature）
- [x] WorldGenVegetationScanner：chunk 加载时扫描原版植被
- [x] BiomeRules 群系植物规则系统（64 个群系配置文件）
- [x] PENDING_TREES 桥接 decoration→chunk-load
- [x] 区块生成优化
- [x] 旧 morphology 系统移除
- [x] 文档全面更新 (2026-06-15)

## P0：工程基础 ✅

- [x] 以 `Ecoflux` 为统一命名，同步修正 `mod_id`、包名和资源命名空间
- [x] 将语言资源迁移到 `assets/ecoflux/`
- [x] 新建 `EcofluxMod` 主入口类
- [x] 补齐基础语言文件（en_us / zh_cn）
- [x] 建立 `EcofluxConstants` 日志与常量类

## P1：区块演替数据骨架 ✅

- [x] 注册 `DataAttachment<SuccessionChunkData>`
- [x] 定义 `SuccessionChunkData`、`PlantQueueEntry`、`ActiveVegetationRecord`
- [x] 实现 NBT 序列化/反序列化
- [x] 区块初始化、加载和保存时机

## P2：数据驱动配置加载 ✅

- [x] 设计 `succession_paths` JSON 格式 (schema v1)
- [x] 实现 `SuccessionConfigLoader` + `SuccessionConfigRegistry`
- [x] 支持根据群系 + 温湿度条件匹配候选路径
- [x] 20 个演替路径 JSON 配置文件
- [x] 中心植物注册表 (PlantRegistry)：plant_definitions + PlantRegistryLoader
- [x] 群系植物规则 (BiomeRules)：biome_rules/ + BiomeRulesLoader + BiomeRulesRegistry

## P3：植物管理闭环 ✅

- [x] 野生植物识别规则（VegetationTypeAdapter 系统）
- [x] 植物队列生成与权重抽取（PlantSpawner.ensureQueue / buildWeightedQueue）
- [x] 在 chunk tick 中尝试种植植物（trySpawnPlant）
- [x] 记录活跃植物（vegetationRecords，activePlants 已退役）
- [x] 监听植物死亡、玩家破坏和失效替换（ModPlayerEvents + pruneInvalidPlants）
- [x] WorldGenVegetationScanner：chunk 加载时扫描原版植被
- [x] PENDING_TREES 桥接：世界生成 → chunk 加载无缝衔接

## P4：积分与进度系统 ✅

- [x] 为不同植物定义 `pointValue`（PlantDefinition）
- [x] 路径配置步长（positiveProgressStep / negativeProgressStep）
- [x] 群系规则配置 consuming（BiomeRules）
- [x] 低频评估调度（evaluation_interval_ticks，EcofluxServerConfig 全局配置）
- [x] 进度增长、衰减和回退逻辑（SuccessionEvaluator）
- [x] 调试命令 `/ecoflux lifecycle inspect/track/observe`

## P5：群系替换与边界处理

- [x] 封装群系替换服务（BiomeTransitionService）
- [x] 正向演替完成时切换到目标群系（applyTransition）
- [x] 负向回退时恢复 fallback 群系（applyRegression）
- [ ] 在区块边界加入轻量混合策略，缓解生硬切换
- [x] 演替完成后重置进度并刷新队列

## P6：同步、调试与兼容

- [x] 客户端视觉状态同步（VegetationVisualChunkSyncPayload）
- [x] 调试命令（/ecoflux auto / visual / lifecycle / sample）
- [x] 钠 (Sodium) 模组兼容 mixin
- [x] Dynamic Trees 兼容
- [ ] GameTest 或可重复验证步骤

---

## 下一步计划

### A. 负向回退 → Fallback 群系切换 ✅

已完成。`SuccessionEvaluator.shouldRegress()` 在 progress ≤ -1.0 时触发，`BiomeTransitionService.applyRegression()` 切换到 fallbackBiome 并重置状态。

### B. activePlants 退役，统一到 vegetationRecords ✅

已完成。移除 `activePlants`、`ActivePlantRecord`、`getTotalPlantPoints()` 等，所有追踪统一到 `vegetationRecords`。

### C. 非玩家方块变更事件接入 VegetationTracker ✅

已完成。pruneInvalidPlants 逐位置检查 BlockState 是否匹配 adapter，prune 以独立间隔运行（prune_interval_ticks，默认 120）。

### D. 区块边界混合

- [ ] 在 `BiomeTransitionService` 中实现 border-blend
- [ ] 过渡期间在区块边缘逐步混合目标群系

### E. 演替路径可视化编辑器

见 `docs/succession-editor.md`。Phase 1 完成，Phase 2 条件分支节点 + 植物快速选择完成（2026-06-09）。

### F. Tree Profile 重构 ✅

2026-06-12 完成。旧 morphology 系统已于 2026-06-14 完全移除，由空间定殖系统取代。

- [x] 创建 `SpaceColonizationProfile`（统一 9 个树种）
- [x] 创建 `MushroomGrowthProfile`（统一 2 个蘑菇）
- [x] 删除旧 profile 文件和 morphology 包
- [x] 更新 `TreeGrowthHandler` 注册逻辑

### G. 世界生成集成 ✅

2026-06-14 完成。

- [x] BiomeModifier 管线：CancelVanillaTrees + AddEcofluxTrees
- [x] EcofluxTreeFeature：世界生成时放置 SC 树
- [x] WorldGenVegetationScanner：扫描并注册世界生成植被
- [x] 密度上限裁剪 + 随机年龄

### H. GameTest / 可重复验证步骤

- [ ] 编写 GameTest 验证完整演替闭环
- [ ] 或编写命令脚本 + 文档化预期结果

---

## 已知待修复项

### spawn_rules.placement ✅
- [x] 从 JSON 和文档中移除未被使用的 `placement` 字段（2026-07-21）

### succession-editor 与最新 schema 同步
编辑器目前仍导出旧格式（含 `plants[]`），需要更新以反映 2026-06-14 的 schema 变更（`plants[]` 移除，`chunk_rules` 简化）。参见 `docs/succession-editor.md` Phase 3。

### 演替系统整合
当前演替循环各部分独立工作（扫描、追踪、生成、修剪、评估、转换），但完整的端到端演替流程（世界生成 → 演替进行 → 群系转换）的整合测试和调试仍在进行中。

---

## 性能分析 (Performance Profiler)

已移除。Tick 级别性能分析现由 `TickProfiler` 提供。
