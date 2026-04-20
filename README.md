# Ecoflux - NeoForge 1.21.1 生态演替模组开发概要

## 模组信息
- **名称**: Ecoflux
- **版本**: 1.0.0
- **Minecraft 版本**: 1.21.1
- **Mod Loader**: NeoForge
- **语言**: Java

## 核心需求
1. **演替区域**：以区块 (16×16) 为最小演替单位。
2. **演替路径**：根据区块中心群系的温度、湿度及当前群系决定演替目标 (JSON配置驱动)。
3. **真实群系替换**：演替触发时调用原版 API 真实修改区块群系。
4. **植物管理**：纳入 Tree、Flower、Mushroom 等野生植物，排除农作物。
5. **植物寿命**：植物具备寿命，自然凋零或被破坏时从管理列表移除。
6. **植物队列**：每个演替区域维护一个植物队列，根据当前群系及权重预先生成待种植物。
7. **点数与进度**：
  - 每株植物提供点数，高级植物点数更高。
  - 区域有 `consuming` 值 (由群系决定)。
  - 植物总点数 > consuming → 演替进度增加；反之减少。
  - 进度满 (≥1.0) 正向演替；进度空 (≤-1.0) 回退演替。
8. **Dynamic Trees 联动** (可选)：若检测到 Dynamic Trees 则联动其生长/凋零/种子机制。

## 核心机制简述

### 植物队列
- 每个区块的 `SuccessionChunkData` 内存储一个 `Queue<PlantQueueEntry>`。
- 队列根据当前群系配置的植物列表按权重随机生成，容量约为 `maxPlantCount * 2`。
- 区块随机刻时，若 `currentPlantCount < maxPlantCount` 且队列非空，则 `poll` 一个植物尝试在区块内合适位置生成。
- 生成后加入 `activePlants` 映射，`currentPlantCount++`。

### 点数与进度计算
- `totalPlantPoints` 为 `activePlants` 中各植物 `pointValue` 之和。
- 区块低频评估 (每 3~7 游戏日) 比较 `totalPlantPoints` 与 `consumingValue`。
- 进度条累加或递减，满值触发群系替换并重置进度与队列。

### 数据存储
- 使用 NeoForge `Data Attachments` 将 `SuccessionChunkData` 附加到 `LevelChunk`。
- 数据包含: 当前/目标/上一群系、进度、植物数量/上限、consuming 值、植物队列、活跃植物映射。

### 配置驱动
- 演替路径及植物列表由 JSON 定义，位于 `data/ecoflux/succession_paths/`。
- 支持同一源群系多条分支 (根据气候条件匹配)。

## 注意事项
- 性能优先：评估低频、分散负载、区块卸载时不计算。
- 边界生硬问题：可在演替触发时对区块边界地表方块进行简单随机混合缓解。
- 联动降级：Dynamic Trees 不存在时自动使用基础植物逻辑。

## 预期实现顺序
1. Data Attachments 框架与 `SuccessionChunkData`。
2. JSON 演替路径加载与匹配。
3. 植物识别、死亡监听、活跃植物映射。
4. 植物队列生成与种植逻辑。
5. 点数评估与演替进度触发。
6. 群系替换 API 调用。
7. 网络同步与可选联动。