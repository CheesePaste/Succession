
Succession
=======

生态演替主题 NeoForge 模组，目标是在不新增自定义群系的前提下，让原版群系在已加载区块内依据温度、湿度、植被覆盖与邻域状态发生渐进式演替。

Project Docs:
==========
- 设计路线图与规范文档：`docs/succession-roadmap-spec.zh-CN.md`

Prototype Validation:
==========
- 当前已实现一个最小原型路径：`minecraft:plains -> minecraft:forest`
- 原型会按固定间隔扫描玩家周围已加载区块，并对符合条件的区块累积进度
- 进度中期会在区块内随机补草、花、树苗，完成后会把当前区块 biome 切到 `forest`，并用随机树木与林下植被把区块装饰得更像森林
- 当前实现是“生态二次修饰 + biome 切换”，不是危险的整区块地形重生成
- 验证命令：
  - `/succession status` 查看当前区块的 biome、进度、评分和阶段
  - `/succession scan` 立即执行一次扫描
  - `/succession accelerate <0.0-1.0>` 手动推进当前区块进度
  - `/succession reset` 重置当前区块原型状态，并尝试把 biome 改回 `plains`

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
