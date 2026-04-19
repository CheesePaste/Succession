已经把“衰退路径 + 衰退区与顶级 forest 的竞争”接进现有原型里了，当前是一个基础但可观测的版本：

- plains 现在会受附近“已完成的 prototype forest”推动，继续向 forest 演替
- 已完成的 prototype forest 不再永远锁死；如果局部林木支撑弱、周边 plains/退化压力更强，会开始衰退
- forest 衰退到阈值后，会反向触发一个 outward retreat 动画，把 forest -> plains
- retreat 过程中会一层层清树、退森林地表，最后做一次 plains 化清理
- 旁边如果仍有稳定 forest，forest 会获得“恢复”而不是继续掉血，形成竞争拉扯

这次核心改动在 src/main/java/com/s/succession/PrototypeSuccessionSystem.java：

- scanChunk(...) 现在不再只识别 growth，还会输出 mode
    - growth：plains 受 forest 压力推动扩张，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:442
    - decay：已成 forest 在竞争中失势，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:501
    - stable：forest 顶住了退化压力，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:504
- 邻域竞争压力通过 sampleNeighborPressure(...) 计算，见 src/main/java/com/s/succession/
  PrototypeSuccessionSystem.java:507
    - forestPressure 来自邻近、且已完成的 prototype forest
    - decayPressure 来自邻近 plains
- progressChunk(...) 里新增了三条分支
    - growth 正向推进，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:270
    - forest_decay 反向扣减 forest 健康，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:291
    - forest_recover 在稳定竞争下缓慢回血，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:310
- 反向塌缩入口在 tryCollapse(...)，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:608
- 动画系统现在支持两种方向
    - ConversionKind.ADVANCE
    - ConversionKind.RETREAT
      见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1454
- retreat 波前的视觉处理：
    - animateDecaySurfaceLayer(...) 做地表退化，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:882
    - removeDecayedTreesOnLayer(...) 一圈圈清树，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1007
    - decorateDecayedChunk(...) / restorePlainSurface(...) 做最终 plains 化收尾，见 src/main/java/com/s/succession/
      PrototypeSuccessionSystem.java:1125 和 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1151

状态输出也更新了：

- /succession status 现在会显示
    - mode
    - forestPressure
    - decayPressure
      见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:175

我已经验证：

- .\gradlew.bat compileJava 通过
- .\gradlew.bat build 通过

当前实现里我做了一个明确取舍：

- 只有“本系统转出来并标记为 completed 的 forest”会进入衰退竞争
- 原生世界里本来就存在的自然 forest，目前不会被这个 prototype 自动拉进衰退路径

这样风险更低，也更容易观察 prototype 行为。

你接下来可以直接测这几种场景：

1. 让一片 plains 靠近已完成的 forest，看它是否更容易被拉成 forest
2. 手工把某个已完成 forest 的树砍掉，看它是否开始掉健康并触发 retreat
3. 在 forest 和大片 plains 接壤的边缘，看 forest_recover 和 forest_decay 哪边占优

如果你要，我下一步可以继续做两个很自然的增强：

1. 给 retreat 也加“更明显的视觉特效”，比如落叶粒子/枯萎感
2. 把竞争从“邻近 8 chunk”扩成“距离衰减场”，让前线更自然而不是偏格子化