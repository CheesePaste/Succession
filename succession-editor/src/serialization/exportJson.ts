import type { GraphNode, PathGraphEdge, SuccessionPath, ClimateCondition } from "../model/types";

function buildPath(
  pathId: string,
  priority: number,
  sourceBiomeId: string,
  targetBiomeId: string,
  climate: ClimateCondition,
  chunkRules: { positiveProgressStep: number; negativeProgressStep: number },
): SuccessionPath {
  return {
    schemaVersion: 1,
    pathId,
    priority,
    sourceBiomes: [sourceBiomeId],
    targetBiome: targetBiomeId,
    fallbackBiome: sourceBiomeId,
    climate: {
      temperature: { ...climate.temperature },
      downfall: { ...climate.downfall },
    },
    chunkRules: {
      positiveProgressStep: chunkRules.positiveProgressStep,
      negativeProgressStep: chunkRules.negativeProgressStep,
    },
  };
}

export function exportPaths(
  nodes: GraphNode[],
  edges: PathGraphEdge[],
): SuccessionPath[] {
  const paths: SuccessionPath[] = [];
  const nodeMap = new Map(nodes.map((n) => [n.id, n]));

  edges.forEach((edge) => {
    const sourceNode = nodeMap.get(edge.source);
    const targetNode = nodeMap.get(edge.target);
    if (!sourceNode || !targetNode) return;
    const d = edge.data!;

    // Case 1: biome → biome — export directly
    if (sourceNode.data.type === "biome" && targetNode.data.type === "biome") {
      paths.push(buildPath(
        d.pathId,
        d.priority,
        sourceNode.data.biomeId,
        targetNode.data.biomeId,
        d.climate,
        d.chunkRules,
      ));
      return;
    }

    // Case 2: condition → biome — walk upstream to find source biome
    if (sourceNode.data.type === "condition" && targetNode.data.type === "biome") {
      const upstreamEdge = edges.find((e) => e.target === sourceNode.id && nodeMap.get(e.source)?.data.type === "biome");
      if (!upstreamEdge) return;
      const srcBiomeNode = nodeMap.get(upstreamEdge.source);
      if (!srcBiomeNode || srcBiomeNode.data.type !== "biome") return;

      // Use condition node's climate; edge may further constrain it if user customized it
      const condC = sourceNode.data.condition;

      paths.push(buildPath(
        d.pathId,
        d.priority,
        srcBiomeNode.data.biomeId,
        targetNode.data.biomeId,
        condC,
        d.chunkRules,
      ));
    }
    // Case 3: biome → condition — skip (connector only)
    // Case 4: condition → condition — skip
  });

  return paths;
}

export function downloadJson(paths: SuccessionPath[]): void {
  const json = JSON.stringify(paths, null, 2);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "succession_paths_export.json";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
