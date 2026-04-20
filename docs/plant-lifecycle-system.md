# Plant Lifecycle System

本文档定义 Ecoflux 当前“植物生命系统（Plant Lifecycle System）”的设计边界、已落地状态与下一步实现方向。

这不是一个只为当前最小原型服务的临时方案，而是后续演替系统、植物阶段推进、树苗到树木转换、兼容原版与外部模组植被行为的统一基础设施。

## 当前结论

- 后续演替逻辑不应直接操作某一类具体植物，而应建立在这个系统之上。
- 这个系统必须提供 **robust 的抽象接口**。
- 这个系统必须可复用到：
  - 地被植物
  - 花
  - 蘑菇
  - 树苗
  - 树木
  - 后续可能的 Dynamic Trees 兼容对象
- 当前骨架已经完成第一段最小落地：
  - `ActiveVegetationRecord`
  - `VegetationTypeAdapter`
  - `VegetationTracker`
  - `SimplePlantAdapter`
  - `SaplingAdapter`
  - `TreeStructureAdapter`
  - 生命周期 -> 视觉层同步入口

## 当前已打通的最小通路

当前已经可以跑通：

1. 原型队列在服务端放置一个植物
2. `VegetationTracker.trackAt(...)` 生成生命周期记录
3. 自动 tick 或命令执行 `observeChunk(...)`
4. adapter 把逻辑阶段映射成 `VegetationVisualState`
5. 服务端把当前 chunk 的生命周期视觉快照同步给客户端
6. 客户端 visual runtime 直接按外部阶段/进度渲染 scale 与 aging tint

这意味着当前生命系统已经不只是文档设计，而是已经开始直接给 visual layer 提供权威阶段输入。

## 为什么需要这个系统

当前最小原型已经验证了：

- 单路径匹配可行
- 单植物生成可行
- 单区块进度结算可行
- 真实 biome 转化可行

但如果继续直接堆更多植物和更多路径，会很快遇到这些问题：

- 花、草、树苗、树木逻辑会越来越碎
- 玩家放置、自然生长、死亡消失没有统一入口
- 演替系统只能依赖“是否有植物”，无法依赖“植物现在处于什么阶段”
- 渲染表现会再次和服务端逻辑缠在一起

所以生命系统必须先成为稳定中间层，然后再让演替和视觉分别复用它。

## 设计原则

### 1. 演替系统与植物生命系统分层

- 生命周期系统负责：世界里一个植被对象怎样出生、成长、成熟、衰老、死亡、转换。
- 演替系统负责：当前区块应该鼓励哪些植物、这些植物在不同阶段如何贡献进度。
- 视觉层负责：当前阶段在客户端应该长什么样。

### 2. 不为单一植物写死逻辑

不能只针对：

- `dandelion`
- `oak_sapling`
- `short_grass`

写一堆分散逻辑。

必须建立统一抽象，让后续不同植被类型共用一套框架。

### 3. 数据驱动优先

后续第一批、第二批、第三批植物不应硬编码在 Java 里，而应通过配置描述：

- 哪些植物属于哪个阶段池
- 哪些植物在什么进度区间出现
- 成熟后贡献多少分
- 衰老/死亡后积分如何变化

### 4. 输入源分层

生命周期系统的来源应收敛为三层：

1. 事件
2. 低频校正扫描
3. 少量关键 Mixin

Mixin 只负责补洞，不直接承载业务。

## 核心概念

### 1. Vegetation

统一使用 `Vegetation` 作为宽泛抽象，而不是只叫 `Plant`。

建议分类至少包括：

- `ground_cover`
- `flower`
- `mushroom`
- `sapling`
- `tree`

### 2. 生命周期阶段

当前统一生命周期枚举为：

- `BORN`
- `JUVENILE`
- `GROWING`
- `MATURE`
- `AGING`
- `DEAD`
- `TRANSFORMED`

### 3. 生命周期记录

`ActiveVegetationRecord` 负责描述一个当前被追踪的植被对象，关键字段包括：

- `vegetationId`
- `adapterType`
- `category`
- `position`
- `lifeStage`
- `birthGameTime`
- `lastObservedGameTime`
- `expireGameTime`
- `basePointValue`
- `currentPointValue`
- `sourceBiomeId`
- `sourcePathId`

作用是让演替系统以后不再只看“有没有植物”，而是能看“当前区块里有哪些阶段的植被”。

## Robust 抽象接口

### 1. `VegetationTypeAdapter`

这是最核心的扩展点。

当前职责已经包括：

- 判断某个 `BlockState` 是否属于自己管理的植被类型
- 从世界状态捕获出生记录
- 观察生命周期变化
- 判断形态转换
- 产出视觉阶段快照

当前接口里最关键的能力有：

```java
public interface VegetationTypeAdapter {
    ResourceLocation typeId();

    VegetationCategory category();

    boolean matches(BlockState state);

    ActiveVegetationRecord captureBirth(...);

    VegetationObservation observe(...);

    VegetationVisualState visualState(...);

    default Optional<VegetationTransformation> detectTransformation(...) { ... }
}
```

为什么这里必须有 `visualState(...)`：

- 不同 adapter 的真实阶段时长并不一致
- 客户端 visual layer 不应自己去猜服务端阶段
- 生命周期系统需要把“逻辑阶段”翻译成“视觉阶段 + 该阶段进度”

### 2. `VegetationTracker`

统一的追踪器，不关心具体植物种类，只关心：

- 注册出生
- 更新观察结果
- 注销死亡
- 处理转换
- 同步到区块数据
- 对外提供区块级视觉同步快照

当前已经有：

- `trackAt(...)`
- `observeTracked(...)`
- `observeChunk(...)`
- `untrack(...)`
- `describeChunk(...)`
- `buildVisualSyncEntries(...)`

### 3. `VegetationObservation`

观察结果对象，表示本次检查一个植被后得到的状态，包含：

- 是否仍然存在
- 当前生命周期阶段
- 当前积分
- 是否成熟
- 是否衰老
- 是否发生转换

它的意义是把“扫描到什么”与“如何处理”解耦。

### 4. `VegetationTransformation`

用于表达形态升级，例如：

- `oak_sapling -> oak_tree`
- 树苗记录 -> 树结构记录

这样树苗变树就不需要再完全重建另一套系统。

### 5. `VegetationVisualState`

这是当前最小通路里新增出来的关键抽象。

职责很单纯：

- 由生命周期 adapter 把逻辑阶段翻译成视觉阶段/进度
- 让客户端 visual layer 只负责表现
- 不让视觉层反向侵入生命系统

## 当前已实现的 adapter 分层

### `SimplePlantAdapter`

负责简单单格植物，例如：

- 花
- 草
- 蘑菇

当前会根据年龄给出：

- `BORN`
- `GROWING`
- `MATURE`
- `AGING`

并同步对应的 `VegetationVisualState`。

### `SaplingAdapter`

负责树苗。

当前会处理：

- 树苗出生
- 树苗观察
- 树苗消失
- 树苗转树结构

### `TreeStructureAdapter`

负责成熟树结构。

当前作为树苗转化后的第一版树对象承接点，已经支持：

- `MATURE`
- `AGING`

## 原版出生 -> 生长 -> 死亡 的接入路线

这里仍建议采用“三层输入源”：

### 第一层：事件层

优先用已有事件收集变化，适合处理：

- 玩家放置
- 玩家破坏
- 方块替换
- 爆炸移除
- 水流 / 活塞等导致的消失

事件层只做一件事：把世界变化转发给 `VegetationTracker`。

### 第二层：低频校正扫描

必须保留这一层。

当前原型里已经有了最小的“观察已追踪位置”的思路，后续应扩展为：

- 优先扫描已追踪植被的位置
- 必要时对少量候选位置做补充扫描
- 不做每 tick 暴力扫描整区块

### 第三层：关键 Mixin 补洞

只在下面情况才考虑 Mixin：

- 事件拿不到
- 扫描成本太高
- 需要精确抓到 vanilla 内部生长完成瞬间

推荐优先考虑的 Mixin 场景：

- `SaplingBlock` 生长成功
- 某些特殊植物随机刻升级
- 树苗到树结构替换的关键节点

## 对演替系统的意义

后续演替系统应该只依赖这些稳定数据：

- 当前区块有哪些活跃植被
- 它们处于什么生命周期阶段
- 它们当前总积分是多少
- 哪类植被正在成熟、死亡或转化

演替系统不应直接依赖：

- 某个 vanilla 方块类名
- 某个具体植物硬编码
- 某个 Mixin 的内部实现细节

## 下一步要做什么

接下来优先补完这几个缺口：

1. 把玩家放置 / 破坏 / 原版自然生长 / 消失事件正式接到 `VegetationTracker`
2. 把当前区块进度结算从 `activePlants` 切到 `vegetationRecords`
3. 给更多 vanilla 植被补 adapter 与视觉阶段映射
4. 为树苗 -> 树结构转换补更稳的检测入口
5. 只在事件与扫描覆盖不足时补必要的 Mixin

## 本文档的决议

- 后续演替应建立在植物生命系统之上
- 该系统必须提供 robust 抽象接口
- 视觉层应复用它给出的阶段快照，而不是自己猜生命周期
- 不应继续使用“每种植物一套逻辑”的方式推进
- 应采用“事件 + 扫描 + 少量关键 Mixin”的组合路线
