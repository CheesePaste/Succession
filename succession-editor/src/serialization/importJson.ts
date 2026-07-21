import type { GraphNode, PathGraphEdge, SuccessionPath } from "../model/types";
import { getBiomeMeta } from "../model/biomeData";

let nodeCounter = 0;
let edgeCounter = 0;

/**
 * Parse an array of SuccessionPath JSON objects into graph nodes and edges.
 */
export function importPaths(
  paths: SuccessionPath[],
): { nodes: GraphNode[]; edges: PathGraphEdge[] } {
  const nodeMap = new Map<string, GraphNode>();
  const edges: PathGraphEdge[] = [];

  // Helper to get or create a node for a biome
  const getOrCreateNode = (biomeId: string): GraphNode => {
    if (nodeMap.has(biomeId)) return nodeMap.get(biomeId)!;

    const meta = getBiomeMeta(biomeId);
    const x = 100 + (nodeMap.size % 5) * 220;
    const y = 80 + Math.floor(nodeMap.size / 5) * 180;

    const node: GraphNode = {
      id: `node_import_${++nodeCounter}`,
      type: "biome",
      position: { x, y },
      data: {
        type: "biome",
        biomeId,
        biomeMeta: meta
          ? {
              defaultTemp: meta.defaultTemp,
              defaultDownfall: meta.defaultDownfall,
              category: meta.category,
              displayName: meta.displayName,
            }
          : undefined,
      },
    };
    nodeMap.set(biomeId, node);
    return node;
  };

  paths.forEach((path) => {
    path.sourceBiomes.forEach((srcBiome) => {
      const sourceNode = getOrCreateNode(srcBiome);
      const targetNode = getOrCreateNode(path.targetBiome);

      const edge: PathGraphEdge = {
        id: `edge_import_${++edgeCounter}`,
        type: "succession",
        source: sourceNode.id,
        target: targetNode.id,
        data: {
          pathId: path.pathId,
          priority: path.priority,
          climate: {
            temperature: { ...path.climate.temperature },
            downfall: { ...path.climate.downfall },
          },
          chunkRules: {
            positiveProgressStep: path.chunkRules.positiveProgressStep,
            negativeProgressStep: path.chunkRules.negativeProgressStep,
          },
        },
      };

      edges.push(edge);
    });
  });

  return { nodes: Array.from(nodeMap.values()), edges };
}

/**
 * Read JSON files from the user's file system and parse them into the graph model.
 */
export function readJsonFiles(files: FileList): Promise<{ nodes: GraphNode[]; edges: PathGraphEdge[] }> {
  const promises = Array.from(files).map(
    (file) =>
      new Promise<SuccessionPath | SuccessionPath[]>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
          try {
            const parsed = JSON.parse(reader.result as string);
            resolve(parsed);
          } catch (e) {
            reject(new Error(`Failed to parse ${file.name}: ${e}`));
          }
        };
        reader.onerror = () => reject(new Error(`Failed to read ${file.name}`));
        reader.readAsText(file);
      }),
  );

  return Promise.all(promises).then((results) => {
    // Flatten: each file can be a single path or an array of paths
    const allPaths: SuccessionPath[] = [];
    results.forEach((r) => {
      if (Array.isArray(r)) {
        allPaths.push(...r);
      } else {
        allPaths.push(r);
      }
    });
    return importPaths(allPaths);
  });
}
