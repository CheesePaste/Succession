# 演替路径可视化编辑器

> 最后更新: 2026-06-16

## 相关文档

修改编辑器前必读：[config-system.md](config-system.md)（JSON schema）

构建一个**综合性可视化配置编辑器**，覆盖 Ecoflux 模组所有可配置内容，取代手写 JSON。

## 设计决策：外部 web 工具 vs 游戏内 GUI

| 维度 | 游戏内 GUI | 外部 web 工具 |
|------|-----------|---------------|
| 开发复杂度 | 手写 widget，不支持复杂图形 | React + ReactFlow，成熟生态 |
| 用户体验 | 受限窗口和分辨率 | 独立窗口，流畅拖拽缩放 |
| 与游戏数据集成 | 直接读取游戏内群系 | 导出 JSON 到 data 目录 |
| 后续扩展性 | 每加一种节点都要手写渲染 | 组件化，易于扩展 |

**决策：外部 web 工具。** 演替路径是设计时配置，开发者编辑 JSON → 丢进游戏验证。预留 HTTP 通信（Phase 4），但不依赖。

## 技术栈

| 层 | 选型 |
|----|------|
| 框架 | React 18+ |
| 语言 | TypeScript 5.x |
| 构建 | Vite 6.x |
| 图形库 | @xyflow/react (ReactFlow 12) |
| 状态 | Zustand（自定义 undo/redo stack） |
| 校验 | 手写（非 Zod） |
| 样式 | CSS |

## 文件结构

```
succession-editor/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── App.css
    ├── index.css
    ├── model/
    │   ├── types.ts          # TS 类型定义（与 Java record 对应）
    │   ├── defaults.ts       # 默认值工厂函数
    │   └── biomeData.ts      # Minecraft 1.21.1 完整群系列表（60+ 群系）
    ├── store/
    │   └── editorStore.ts    # Zustand store + undo/redo + 校验
    ├── serialization/
    │   ├── exportJson.ts     # GraphModel → JSON
    │   └── importJson.ts     # JSON → GraphModel
    ├── i18n/
    │   ├── translations.ts   # 中英文翻译表（~90 keys）
    │   └── I18nContext.tsx
    └── components/
        ├── ErrorBoundary.tsx
        ├── canvas/
        │   ├── GraphCanvas.tsx
        │   ├── BiomeNode.tsx
        │   ├── ConditionNode.tsx
        │   └── SuccessionEdge.tsx
        ├── palette/
        │   └── BiomePalette.tsx
        ├── panel/
        │   ├── PropertyPanel.tsx
        │   ├── NoSelection.tsx
        │   ├── NodeProperties.tsx
        │   ├── PathProperties.tsx
        │   ├── ConditionProperties.tsx
        │   └── editors/
        │       ├── PathIdentityEditor.tsx
        │       ├── ClimateEditor.tsx
        │       ├── ChunkRulesEditor.tsx
        │       ├── PlantTableEditor.tsx
        │       └── SpawnRulesEditor.tsx
        └── toolbar/
            └── Toolbar.tsx
```

## 功能列表（按 Phase）

### Phase 1：基础编辑器 ✅

- [x] Vite + React + TypeScript + @xyflow/react + Zustand 脚手架
- [x] 完整类型定义（与 Java record 一一对应）
- [x] Minecraft 1.21.1 完整群系列表（60+ 群系，含默认温湿度）
- [x] Zustand store：节点/连线增删改、undo/redo、校验
- [x] 三栏布局：左侧群系面板 + 中间图画布 + 右侧属性面板
- [x] 自定义 BiomeNode 渲染（按温度着色）
- [x] 自定义 SuccessionEdge 渲染（路径标签、选中高亮）
- [x] 属性面板 — Path Identity、Climate、ChunkRules、Plant Table 编辑器
- [x] JSON 导出（兼容 schema v1，含简化的 chunk_rules）
- [x] JSON 导入（解析现有文件 → 还原为图）
- [x] 校验（pathId 唯一、数值范围、必填字段）+ 错误高亮
- [x] 撤销/重做、键盘快捷键（Ctrl+Z/Y、Delete、Escape）
- [x] 中英双语、错误边界

### Phase 2：条件分支节点 + 植物快速选择 ✅

- [x] ClimateConditionNode 菱形节点（match/no_match 双输出端口）
- [x] ConditionProperties 编辑器
- [x] PlantTableEditor quick-add 下拉框（48 种常见植物按 category 分组）
- [x] 导出格式同步 2026-06-14 schema（移除 plants[]，简化 chunk_rules）

### Phase 3：BiomeRules 编辑器 ✅

- [x] BiomeRules 页面：搜索栏 + 表格总览 + 详情编辑面板
- [x] 支持导入/导出 biome_rules JSON（兼容单文件和多文件格式）
- [x] 植物列表配置复用 PlantTableEditor
- [x] 页面 tab 切换（演替路径 / 群系规则）

## 需要继续的

### 编辑器落伍修复

- [x] 导出格式更新：移除 `plants[]`、简化 `chunkRules`（2026-07-21）
- [x] `placement` 字段从编辑器移除（对应 Java 端清理）
- [x] 添加页面切换 Tab 栏

### Phase 3 未完成

- [ ] PriorityRouterNode
- [ ] 全局 DAG 预览（所有路径汇总）
- [ ] 群系覆盖分析

### Phase 4：游戏内集成（可选）

- [ ] PathEditorHttpServer（Java 端）
- [ ] 一键推送 + `/reload` 热加载
- [ ] 获取游戏内群系/气候数据

## 启动方式

```bash
cd succession-editor
npm run dev     # 开发服务器
npm run build   # 生产构建
```

## 与现有系统的对接

- 编辑器产出 JSON 直接放入 `src/main/resources/data/ecoflux/succession_paths/`
- `SuccessionConfigLoader` 无需任何修改
- 导出格式与现有 JSON 相同（schema v1，简化的 chunk_rules）
- 编辑器项目独立于 Minecraft mod，位于 `succession-editor/`
- 植物配置已移至 `biome_rules/`，路径 JSON 不再包含 `plants[]`（见 Phase 3 备注）
