# 开发文档索引

这些文档基于当前分支的 `README.md`、现有工程骨架和当前工作区状态整理，用来给后续开发提供统一上下文。

## 工作规则

- 当前阶段只以 `README.md` 和当前分支工作区为依据开展开发。
- 明确禁止查看、比对、引用其他 Git 分支的内容；如果未来确实需要跨分支信息，必须先获得用户明确许可。
- 当 `README.md`、构建配置、资源命名存在冲突时，先记录差异，再由后续开发任务显式消解，不擅自猜测旧分支实现。

## 文档列表

- `docs/development-context.md`：项目目标、当前仓库状态、已知约束和待确认事项。
- `docs/architecture.md`：面向实现的模块划分、数据结构建议、运行时流程和资源布局。
- `docs/todolist.md`：按优先级整理的开发待办、验收点和里程碑。

## 当前快照

- 项目是 NeoForge 1.21.1 模组骨架，Java 代码尚未开始实现。
- `README.md` 已明确核心玩法：区块级生态演替、植物积分、JSON 驱动路径、可选 Dynamic Trees 联动。
- 命名现已确定统一为 `Ecoflux`，后续开发应逐步清理当前工程里残留的 `Succession` / `succession` 命名。
- 后续实现建议先阅读 `docs/development-context.md`，再按 `docs/architecture.md` 和 `docs/todolist.md` 推进。
