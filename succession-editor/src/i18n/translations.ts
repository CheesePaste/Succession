export type Lang = "zh" | "en";

export interface Translations {
  // Toolbar
  "app.title": string;
  "btn.import": string;
  "btn.export": string;
  "btn.undo": string;
  "btn.redo": string;
  "btn.validate": string;
  "btn.clear": string;
  "btn.lang": string;
  "msg.validationPassed": string;
  "msg.validationErrors": string;
  "msg.clearConfirm": string;
  "msg.importFailed": string;

  // Biome Palette
  "palette.title": string;
  "palette.search": string;
  "palette.biomeCount": string;
  "palette.onCanvas": string;
  "palette.alreadyUsed": string;
  "palette.clickToAdd": string;
  "dimension.overworld": string;
  "dimension.nether": string;
  "dimension.end": string;

  // Property Panel
  "panel.titleEdge": string;
  "panel.titleNode": string;
  "panel.titleNone": string;

  // No Selection
  "overview.title": string;
  "overview.biomeNodes": string;
  "overview.paths": string;
  "overview.sourceBiomes": string;
  "overview.targetBiomes": string;
  "overview.biomesOnCanvas": string;
  "overview.pathsList": string;
  "overview.plantsSuffix": string;
  "overview.isolated": string;
  "overview.emptyHint": string;

  // Node Properties
  "node.biomeInfo": string;
  "node.biomeId": string;
  "node.category": string;
  "node.defaultTemp": string;
  "node.defaultDownfall": string;
  "node.connections": string;
  "node.totalEdges": string;
  "node.outgoing": string;
  "node.incoming": string;
  "node.removeNode": string;

  // Path Properties
  "path.identity": string;
  "path.pathId": string;
  "path.priority": string;
  "path.sourceBiome": string;
  "path.targetBiome": string;
  "path.fallbackBiome": string;
  "path.fallbackAuto": string;
  "path.climate": string;
  "path.tempRange": string;
  "path.downfallRange": string;
  "path.climateErrorTemp": string;
  "path.climateErrorDownfall": string;
  "path.chunkRules": string;
  "path.positiveStep": string;
  "path.negativeStep": string;
  "path.plants": string;
  "path.totalWeight": string;
  "path.noPlants": string;
  "path.addPlant": string;
  "path.removePath": string;

  // Plant Table
  "plant.headerIndex": string;
  "plant.headerId": string;
  "plant.headerWeight": string;
  "plant.headerPoints": string;
  "plant.headerAge": string;
  "plant.headerActions": string;
  "plant.removeHint": string;
  "plant.category": string;
  "plant.maxAgeTicks": string;
  "plant.spawnRules": string;
  "plant.spawnSummary": string;
  "plant.weightOnly": string;

  // Spawn Rules
  "spawn.placement": string;
  "spawn.density": string;
  "spawn.requireSky": string;
  "spawn.allowedBase": string;
  "spawn.addBlock": string;
  "spawn.blockPlaceholder": string;

  // Validation
  "validation.duplicatePathId": string;
  "validation.pathIdRequired": string;
  "validation.priorityNegative": string;
  "validation.tempMinMax": string;
  "validation.downfallMinMax": string;
  "validation.positiveStep": string;
  "validation.negativeStep": string;
  "validation.isolatedBiome": string;

  // Plant Quick Add
  "plant.quickAdd": string;
  "plant.addCustom": string;

  // Condition Node
  "condition.title": string;
  "condition.label": string;
  "condition.labelHint": string;
  "condition.climate": string;
  "condition.matchDesc": string;
  "condition.noMatchDesc": string;
  "condition.delete": string;
  "condition.incomingFrom": string;
  "condition.outgoingTo": string;
  "condition.matchBranch": string;
  "condition.noMatchBranch": string;

  // Palette Branching
  "palette.branching": string;
  "palette.conditionNode": string;
  "palette.conditionDesc": string;

  // Error Boundary
  "error.title": string;
  "error.reload": string;

  // Tabs
  "tab.paths": string;
  "tab.biomes": string;
}

const zh: Translations = {
  "app.title": "🌿 Ecoflux 编辑器",
  "btn.import": "📥 导入 JSON",
  "btn.export": "📤 导出 JSON",
  "btn.undo": "↩ 撤销",
  "btn.redo": "↪ 重做",
  "btn.validate": "✓ 校验",
  "btn.clear": "🗑 清空",
  "btn.lang": "🌐 EN",
  "msg.validationPassed": "校验通过，无错误。",
  "msg.validationErrors": "{errors} 个错误, {warnings} 个警告。请查看高亮项。",
  "msg.clearConfirm": "确定要清空整个图吗？",
  "msg.importFailed": "导入失败: {error}",

  "palette.title": "🌍 群系面板",
  "palette.search": "搜索群系...",
  "palette.biomeCount": "{count} 个群系",
  "palette.onCanvas": "画布上 {count} 个",
  "palette.alreadyUsed": "已在画布上",
  "palette.clickToAdd": "点击添加",
  "dimension.overworld": "主世界",
  "dimension.nether": "下界",
  "dimension.end": "末地",

  "panel.titleEdge": "📝 连线: {pathId}",
  "panel.titleNode": "📍 节点: {name}",
  "panel.titleNone": "📋 属性",

  "overview.title": "图概览",
  "overview.biomeNodes": "群系节点",
  "overview.paths": "路径 (连线)",
  "overview.sourceBiomes": "源群系",
  "overview.targetBiomes": "目标群系",
  "overview.biomesOnCanvas": "画布上的群系",
  "overview.pathsList": "路径列表",
  "overview.plantsSuffix": " 种植物",
  "overview.isolated": "孤立",
  "overview.emptyHint": "从左侧面板添加群系，然后连线创建演替路径。",

  "node.biomeInfo": "群系信息",
  "node.biomeId": "群系 ID",
  "node.category": "分类",
  "node.defaultTemp": "默认温度",
  "node.defaultDownfall": "默认湿度",
  "node.connections": "连接",
  "node.totalEdges": "连线总数",
  "node.outgoing": "出向路径",
  "node.incoming": "入向路径",
  "node.removeNode": "🗑 删除节点",

  "path.identity": "路径标识",
  "path.pathId": "path_id",
  "path.priority": "priority",
  "path.sourceBiome": "源群系",
  "path.targetBiome": "目标群系",
  "path.fallbackBiome": "fallback_biome",
  "path.fallbackAuto": "自动: 源群系",
  "path.climate": "气候条件",
  "path.tempRange": "🌡 温度范围",
  "path.downfallRange": "💧 湿度范围",
  "path.climateErrorTemp": "⚠ 温度: min > max",
  "path.climateErrorDownfall": "⚠ 湿度: min > max",
  "path.chunkRules": "区块规则",
  "path.positiveStep": "正向进度步长",
  "path.negativeStep": "负向进度步长",
  "path.plants": "植物",
  "path.totalWeight": "总权重: {total}",
  "path.noPlants": "暂无植物，请添加。",
  "path.addPlant": "+ 添加植物",
  "path.removePath": "🗑 删除路径",

  "plant.headerIndex": "#",
  "plant.headerId": "植物 ID",
  "plant.headerWeight": "权重",
  "plant.headerPoints": "积分",
  "plant.headerAge": "寿命",
  "plant.headerActions": "",
  "plant.removeHint": "移除植物",
  "plant.category": "分类",
  "plant.maxAgeTicks": "最大寿命 (tick)",
  "plant.spawnRules": "生成规则",
  "plant.spawnSummary": "天空:{sky} / {blocks} 基底",
  "plant.weightOnly": "仅权重",

  "spawn.placement": "放置方式",
  "spawn.density": "最大局部密度",
  "spawn.requireSky": "需要天空光照",
  "spawn.allowedBase": "允许的基底方块",
  "spawn.addBlock": "+",
  "spawn.blockPlaceholder": "minecraft:...",

  "validation.duplicatePathId": "重复的 path_id: {pathId}",
  "validation.pathIdRequired": "path_id 不能为空",
  "validation.priorityNegative": "priority 必须 >= 0",
  "validation.tempMinMax": "温度 min > max",
  "validation.downfallMinMax": "湿度 min > max",
  "validation.positiveStep": "positiveProgressStep 必须 > 0",
  "validation.negativeStep": "negativeProgressStep 必须 > 0",
  "validation.isolatedBiome": "孤立的群系: {biome}",

  "plant.quickAdd": "快速添加植物",
  "plant.addCustom": "+ 自定义植物",

  "condition.title": "🔷 条件: {label}",
  "condition.label": "标签",
  "condition.labelHint": "条件节点名称",
  "condition.climate": "气候条件",
  "condition.matchDesc": "绿色端口 (左侧): 气候匹配时走向此分支",
  "condition.noMatchDesc": "红色端口 (右侧): 气候不匹配时走向此分支",
  "condition.delete": "🗑 删除条件节点",
  "condition.incomingFrom": "入向连线",
  "condition.outgoingTo": "出向分支",
  "condition.matchBranch": "匹配 ✓",
  "condition.noMatchBranch": "不匹配 ✗",

  "palette.branching": "🔀 分支",
  "palette.conditionNode": "气候条件",
  "palette.conditionDesc": "菱形节点，匹配/不匹配双端口",

  "error.title": "编辑器崩溃",
  "error.reload": "重新加载编辑器",

  "tab.paths": "演替路径",
  "tab.biomes": "群系规则",
};

const en: Translations = {
  "app.title": "🌿 Ecoflux Editor",
  "btn.import": "📥 Import JSON",
  "btn.export": "📤 Export JSON",
  "btn.undo": "↩ Undo",
  "btn.redo": "↪ Redo",
  "btn.validate": "✓ Validate",
  "btn.clear": "🗑 Clear",
  "btn.lang": "🌐 中文",
  "msg.validationPassed": "Validation passed! No errors.",
  "msg.validationErrors": "{errors} error(s), {warnings} warning(s). See highlighted items.",
  "msg.clearConfirm": "Clear the entire graph?",
  "msg.importFailed": "Import failed: {error}",

  "palette.title": "🌍 Biome Palette",
  "palette.search": "Search biomes...",
  "palette.biomeCount": "{count} biomes",
  "palette.onCanvas": "{count} on canvas",
  "palette.alreadyUsed": "Already on canvas",
  "palette.clickToAdd": "Click to add",
  "dimension.overworld": "Overworld",
  "dimension.nether": "Nether",
  "dimension.end": "The End",

  "panel.titleEdge": "📝 Edge: {pathId}",
  "panel.titleNode": "📍 Node: {name}",
  "panel.titleNone": "📋 Properties",

  "overview.title": "Graph Overview",
  "overview.biomeNodes": "Biome Nodes",
  "overview.paths": "Paths (Edges)",
  "overview.sourceBiomes": "Source Biomes",
  "overview.targetBiomes": "Target Biomes",
  "overview.biomesOnCanvas": "Biomes on Canvas",
  "overview.pathsList": "Paths",
  "overview.plantsSuffix": " plants",
  "overview.isolated": "isolated",
  "overview.emptyHint": "Add biomes from the left palette and connect them to create succession paths.",

  "node.biomeInfo": "Biome Info",
  "node.biomeId": "Biome ID",
  "node.category": "Category",
  "node.defaultTemp": "Default Temp",
  "node.defaultDownfall": "Default Downfall",
  "node.connections": "Connections",
  "node.totalEdges": "Total Edges",
  "node.outgoing": "Outgoing Paths",
  "node.incoming": "Incoming Paths",
  "node.removeNode": "🗑 Remove Node",

  "path.identity": "Path Identity",
  "path.pathId": "path_id",
  "path.priority": "priority",
  "path.sourceBiome": "Source Biome",
  "path.targetBiome": "Target Biome",
  "path.fallbackBiome": "fallback_biome",
  "path.fallbackAuto": "Auto: source biome",
  "path.climate": "Climate Conditions",
  "path.tempRange": "🌡 Temperature Range",
  "path.downfallRange": "💧 Downfall Range",
  "path.climateErrorTemp": "⚠ Temperature: min > max",
  "path.climateErrorDownfall": "⚠ Downfall: min > max",
  "path.chunkRules": "Chunk Rules",
  "path.positiveStep": "positive_progress_step",
  "path.negativeStep": "negative_progress_step",
  "path.plants": "Plants",
  "path.totalWeight": "total weight: {total}",
  "path.noPlants": "No plants yet. Add one below.",
  "path.addPlant": "+ Add Plant",
  "path.removePath": "🗑 Remove Path",

  "plant.headerIndex": "#",
  "plant.headerId": "Plant ID",
  "plant.headerWeight": "Wt",
  "plant.headerPoints": "Pts",
  "plant.headerAge": "Age",
  "plant.headerActions": "",
  "plant.removeHint": "Remove plant",
  "plant.category": "Category",
  "plant.maxAgeTicks": "Max Age (ticks)",
  "plant.spawnRules": "Spawn Rules",
  "plant.spawnSummary": "sky:{sky} / {blocks} bases",
  "plant.weightOnly": "weight only",

  "spawn.placement": "Placement",
  "spawn.density": "Max Local Density",
  "spawn.requireSky": "Require Sky Access",
  "spawn.allowedBase": "Allowed Base Blocks",
  "spawn.addBlock": "+ Add",
  "spawn.blockPlaceholder": "minecraft:...",

  "validation.duplicatePathId": "Duplicate path_id: {pathId}",
  "validation.pathIdRequired": "path_id is required",
  "validation.priorityNegative": "priority must be >= 0",
  "validation.tempMinMax": "temperature min > max",
  "validation.downfallMinMax": "downfall min > max",
  "validation.positiveStep": "positiveProgressStep must be > 0",
  "validation.negativeStep": "negativeProgressStep must be > 0",
  "validation.isolatedBiome": "Isolated biome: {biome}",

  "plant.quickAdd": "Quick Add Plant",
  "plant.addCustom": "+ Custom Plant",

  "condition.title": "🔷 Condition: {label}",
  "condition.label": "Label",
  "condition.labelHint": "Condition node name",
  "condition.climate": "Climate Conditions",
  "condition.matchDesc": "Green port (left): if climate matches",
  "condition.noMatchDesc": "Red port (right): if climate doesn't match",
  "condition.delete": "🗑 Delete Condition",
  "condition.incomingFrom": "Incoming Edge",
  "condition.outgoingTo": "Outgoing Branches",
  "condition.matchBranch": "Match ✓",
  "condition.noMatchBranch": "No Match ✗",

  "palette.branching": "🔀 Branching",
  "palette.conditionNode": "Climate Condition",
  "palette.conditionDesc": "Diamond node, match/no-match outputs",

  "error.title": "Editor Crashed",
  "error.reload": "Reload Editor",

  "tab.paths": "Succession Paths",
  "tab.biomes": "Biome Rules",
};

export const translations: Record<Lang, Translations> = { zh, en };

const LANG_KEY = "ecoflux-editor-lang";

export function getSavedLang(): Lang {
  try {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === "zh" || saved === "en") return saved;
  } catch {}
  return "zh";
}

export function saveLang(lang: Lang): void {
  try {
    localStorage.setItem(LANG_KEY, lang);
  } catch {}
}
