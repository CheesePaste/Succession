• 已经按你这次的意思改成“plains 是中值”的双向原型了：

- 正向：plains -> forest
- 反向：plains -> desert
- 两边都会对周围产生压力，形成竞争
- 目前方向选择先按“概率 + 邻域压力”做，适合你在超平坦里先观察

这次核心调整

- PrototypeChunkState 现在是“有符号进度”
  - 正进度代表朝 forest
  - 负进度代表朝 desert
  - 见 src/main/java/com/s/succession/PrototypeChunkState.java:51
- 状态阶段名也变成了双向
  - 例如 forest_mid / desert_late / converted_forest / converted_desert
  - 见 src/main/java/com/s/succession/PrototypeChunkState.java:103

扫描与竞争逻辑

- /succession status 现在会显示：
  - forestPressure
  - desertPressure
  - 当前 mode
  - 中值路径结构
  - 见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:176
- plains 不再只会往 forest 走了
  - 在 scanChunk(...) 里，plains 会先做一个概率分支：
    - PLAINS_TO_FOREST
    - PLAINS_TO_DESERT
    - 或本轮保持中立
  - 见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:438
  - 概率核心在 rollPlainDirection(...)
  - 见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1532
- 邻域竞争现在是 forest vs desert
  - 统计邻近已完成 forest / desert chunk 压力
  - 见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:516

推进与回退

- plains 收到正向概率时，进度向正侧推，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:273
- plains 收到负向概率时，进度向负侧推，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:283
- 已完成 forest 遇到 desert 压力会开始回退 toward plains，见 src/main/java/com/s/succession/
  PrototypeSuccessionSystem.java:293
- 已完成 desert 遇到 forest 压力也会回退 toward plains，见 src/main/java/com/s/succession/
  PrototypeSuccessionSystem.java:301
- 中立期没被明显拉动时，会往 0 漂回，也就是回到 plains 中值，见 src/main/java/com/s/succession/
  PrototypeSuccessionSystem.java:321 和 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1522

动画与地表

- 转成 forest 的 outward 动画还保留
- 现在也支持转成 desert 的 outward 动画
- forest/desert 回退到 plains 也都支持 outward retreat
- 动画种类扩展到了 4 种，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1644
- desert 完成后的收尾装饰加上了，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1246
- plains 恢复收尾也单独整理了，见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:1238

为了你测试方便，我顺手也改了命令

- /succession accelerate 现在支持负值
  - 也就是你可以直接手动往 desert 侧推
  - 见 src/main/java/com/s/succession/PrototypeSuccessionSystem.java:65

你在超平坦里现在可以这么测

- 正向测试：
  - /succession accelerate 0.5
  - 多来几次，看是否进 forest 分支
- 负向测试：
  - /succession accelerate -0.5
  - 多来几次，看是否进 desert 分支
- 重置：
  - /succession reset
- 状态观察：
  - /succession status

我已经验证：

- .\gradlew.bat compileJava 通过
- .\gradlew.bat build 通过

当前这个版本是“先可观测、先能竞争”的原型，特征是：

- plains 的去向先由概率和邻域压力决定
- 很适合超平坦做前线观察
- 还不是最终生态学模型，但已经能看到 forest / desert 抢 plains

如果你继续，我建议下一步直接做这两个里的一个：

1. 给 plains 的正负概率再加“温湿度假参数 / 噪声场”，让超平坦里前线更自然
2. 做一个 /succession debugfrontier，直接打印当前 chunk 是偏 forest 还是偏 desert，以及本轮概率是多少