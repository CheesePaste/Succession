import type {
  ClimateCondition,
  ChunkRules,
  PlantSpawnRules,
  PlantDefinition,
  PathEdgeData,
  ConditionNodeData,
} from "./types";

export function defaultFloatRange(min = 0, max = 1): { min: number; max: number } {
  return { min, max };
}

export function defaultIntRange(min = 1, max = 5): { min: number; max: number } {
  return { min, max };
}

export function defaultClimate(): ClimateCondition {
  return {
    temperature: { min: 0.6, max: 0.95 },
    downfall: { min: 0.4, max: 0.9 },
  };
}

export function defaultChunkRules(): ChunkRules {
  return {
    positiveProgressStep: 0.25,
    negativeProgressStep: 0.25,
  };
}

export function defaultSpawnRules(): PlantSpawnRules {
  return {
    requireSky: true,
    maxLocalDensity: 4,
    allowedBaseBlocks: ["minecraft:grass_block", "minecraft:dirt"],
  };
}

export function defaultPlant(
  plantId = "minecraft:short_grass",
  overrides?: Partial<PlantDefinition>,
): PlantDefinition {
  const info = inferPlantCategory(plantId);
  return {
    plantId,
    category: info.category,
    weight: info.weight,
    pointValue: info.pointValue,
    maxAgeTicks: info.maxAgeTicks,
    spawnRules: defaultSpawnRules(),
    ...overrides,
  };
}

const PLANT_CATEGORY_RULES: Array<{ test: (id: string) => boolean; category: string; weight: number; pointValue: number; maxAgeTicks: number }> = [
  { test: (id) => id.includes("grass") || id.includes("fern") || id.includes("dead_bush"), category: "ground_cover", weight: 8, pointValue: 1, maxAgeTicks: 72000 },
  { test: (id) => id.includes("sapling") || id.includes("propagule"), category: "sapling", weight: 2, pointValue: 3, maxAgeTicks: 120000 },
  { test: (id) => id.includes("mushroom"), category: "mushroom", weight: 3, pointValue: 2, maxAgeTicks: 36000 },
  { test: (id) => /tulip|orchid|allium|bluet|daisy|dandelion|poppy|cornflower|lily|rose|sunflower|lilac|peony|wither/.test(id), category: "flower", weight: 4, pointValue: 2, maxAgeTicks: 48000 },
  { test: (id) => id.includes("cactus") || id.includes("sugar_cane") || id.includes("bamboo") || id.includes("vine") || id.includes("lily_pad"), category: "vine", weight: 2, pointValue: 1, maxAgeTicks: 72000 },
];

export function inferPlantCategory(plantId: string): { category: string; weight: number; pointValue: number; maxAgeTicks: number } {
  for (const rule of PLANT_CATEGORY_RULES) {
    if (rule.test(plantId)) {
      return { category: rule.category, weight: rule.weight, pointValue: rule.pointValue, maxAgeTicks: rule.maxAgeTicks };
    }
  }
  return { category: "ground_cover", weight: 8, pointValue: 1, maxAgeTicks: 72000 };
}

export function defaultConditionData(): ConditionNodeData {
  return {
    type: "condition",
    label: "Climate Check",
    condition: defaultClimate(),
  };
}

export function defaultEdgeData(
  sourceBiome: string,
  targetBiome: string,
): PathEdgeData {
  const srcName = sourceBiome.replace("minecraft:", "");
  const tgtName = targetBiome.replace("minecraft:", "");
  return {
    pathId: `ecoflux:${srcName}_to_${tgtName}`,
    priority: 10,
    climate: defaultClimate(),
    chunkRules: defaultChunkRules(),
  };
}
