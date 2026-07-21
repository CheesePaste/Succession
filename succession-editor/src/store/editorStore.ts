import { create } from "zustand";
import type { NodeChange, EdgeChange } from "@xyflow/react";
import { applyNodeChanges, applyEdgeChanges } from "@xyflow/react";
import type { BiomeGraphNode, GraphNode, PathGraphEdge, PathEdgeData, ConditionGraphNode, ValidationError } from "../model/types";
import { getBiomeMeta } from "../model/biomeData";
import { defaultEdgeData, defaultConditionData } from "../model/defaults";

let nextNodeId = 0;
let nextEdgeId = 0;

function genNodeId(): string {
  return `node_${++nextNodeId}`;
}

function genEdgeId(): string {
  return `edge_${++nextEdgeId}`;
}

export function resetIdCounters(): void {
  nextNodeId = 0;
  nextEdgeId = 0;
}

interface HistoryEntry {
  nodes: GraphNode[];
  edges: PathGraphEdge[];
}

interface EditorState {
  nodes: GraphNode[];
  edges: PathGraphEdge[];
  selectedId: string | null;
  validationErrors: ValidationError[];

  undoStack: HistoryEntry[];
  redoStack: HistoryEntry[];
  undo: () => void;
  redo: () => void;
  pushHistory: () => void;

  onNodesChange: (changes: NodeChange<GraphNode>[]) => void;
  onEdgesChange: (changes: EdgeChange<PathGraphEdge>[]) => void;

  addBiomeNode: (biomeId: string) => string;
  addConditionNode: (position?: { x: number; y: number }) => string;
  removeNode: (nodeId: string) => void;

  addEdge: (sourceId: string, targetId: string, sourceHandle?: string, targetHandle?: string) => string | null;
  removeEdge: (edgeId: string) => void;
  updateEdgeData: (edgeId: string, patch: Partial<PathEdgeData>) => void;

  setSelectedId: (id: string | null) => void;

  loadGraph: (nodes: GraphNode[], edges: PathGraphEdge[]) => void;
  clearGraph: () => void;

  validate: () => boolean;
  clearValidation: () => void;

  getSelectedEdge: () => PathGraphEdge | undefined;
  getConnectedEdges: (nodeId: string) => PathGraphEdge[];
}

export const useEditorStore = create<EditorState>()((set, get) => ({
  nodes: [],
  edges: [],
  selectedId: null,
  validationErrors: [],
  undoStack: [],
  redoStack: [],

  pushHistory() {
    const { nodes, edges, undoStack } = get();
    set({
      undoStack: [...undoStack.slice(-49), { nodes, edges }],
      redoStack: [],
    });
  },

  undo() {
    const { undoStack, nodes, edges } = get();
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    set({
      undoStack: undoStack.slice(0, -1),
      redoStack: [...get().redoStack, { nodes, edges }],
      nodes: prev.nodes,
      edges: prev.edges,
      selectedId: null,
    });
  },

  redo() {
    const { redoStack, nodes, edges } = get();
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    set({
      redoStack: redoStack.slice(0, -1),
      undoStack: [...get().undoStack, { nodes, edges }],
      nodes: next.nodes,
      edges: next.edges,
      selectedId: null,
    });
  },

  onNodesChange(changes) {
    const { nodes } = get();
    const nextNodes = applyNodeChanges(changes, nodes) as GraphNode[];
    set({ nodes: nextNodes });

    // Handle removals from ReactFlow (e.g., Delete key)
    const removedIds = changes
      .filter((c) => c.type === "remove")
      .map((c) => c.id);
    if (removedIds.length > 0) {
      const { edges } = get();
      const removedSet = new Set(removedIds);
      set({
        edges: edges.filter((e) => !removedSet.has(e.source) && !removedSet.has(e.target)),
      });
    }
  },

  onEdgesChange(changes) {
    const { edges } = get();
    const nextEdges = applyEdgeChanges(changes, edges) as PathGraphEdge[];
    set({ edges: nextEdges });
  },

  addBiomeNode(biomeId) {
    get().pushHistory();
    const id = genNodeId();
    const meta = getBiomeMeta(biomeId);
    const node: BiomeGraphNode = {
      id,
      type: "biome",
      position: { x: 100 + Math.random() * 300, y: 100 + Math.random() * 300 },
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
    set({ nodes: [...get().nodes, node] });
    return id;
  },

  addConditionNode(position) {
    get().pushHistory();
    const id = genNodeId();
    const data = defaultConditionData();
    const node: ConditionGraphNode = {
      id,
      type: "condition",
      position: position ?? { x: 200 + Math.random() * 200, y: 200 + Math.random() * 200 },
      data,
    };
    set({ nodes: [...get().nodes, node] });
    return id;
  },

  removeNode(nodeId) {
    get().pushHistory();
    set({
      nodes: get().nodes.filter((n) => n.id !== nodeId),
      edges: get().edges.filter((e) => e.source !== nodeId && e.target !== nodeId),
      selectedId: get().selectedId === nodeId ? null : get().selectedId,
    });
  },

  addEdge(sourceId, targetId, sourceHandle?, targetHandle?) {
    const existing = get().edges.find(
      (e) => e.source === sourceId && e.target === targetId,
    );
    if (existing) return null;

    const sourceNode = get().nodes.find((n) => n.id === sourceId);
    const targetNode = get().nodes.find((n) => n.id === targetId);
    if (!sourceNode || !targetNode) return null;

    get().pushHistory();
    const id = genEdgeId();

    const isConditionSource = sourceNode.data?.type === "condition";
    const isConditionTarget = targetNode.data?.type === "condition";

    let data: PathEdgeData;
    if (isConditionSource) {
      const targetBiomeId = (targetNode.data as any).biomeId ?? "";
      data = {
        ...defaultEdgeData("", targetBiomeId),
        pathId: `ecoflux:cond_${targetBiomeId}`,
        conditionBranch: (sourceHandle === "match" ? "match" : "no_match") as "match" | "no_match",
        parentConditionId: sourceId,
        sourceBiome: "",
        targetBiome: targetBiomeId,
      };
    } else if (isConditionTarget) {
      const sourceBiomeId = (sourceNode.data as any).biomeId ?? "";
      data = {
        ...defaultEdgeData(sourceBiomeId, ""),
        pathId: `ecoflux:${sourceBiomeId.replace("minecraft:", "")}_to_cond`,
        sourceBiome: sourceBiomeId,
        targetBiome: "",
      };
    } else {
      const srcBiomeId = (sourceNode.data as any).biomeId ?? "";
      const tgtBiomeId = (targetNode.data as any).biomeId ?? "";
      data = defaultEdgeData(srcBiomeId, tgtBiomeId);
    }

    const edge: PathGraphEdge = {
      id,
      type: "succession",
      source: sourceId,
      target: targetId,
      sourceHandle: sourceHandle ?? undefined,
      targetHandle: targetHandle ?? undefined,
      data,
    };
    set({ edges: [...get().edges, edge] });
    return id;
  },

  removeEdge(edgeId) {
    get().pushHistory();
    set({
      edges: get().edges.filter((e) => e.id !== edgeId),
      selectedId: get().selectedId === edgeId ? null : get().selectedId,
    });
  },

  updateEdgeData(edgeId, patch) {
    const updated = get().edges.map((e) => {
      if (e.id !== edgeId) return e;
      return { ...e, data: { ...e.data, ...patch } } as PathGraphEdge;
    });
    set({ edges: updated });
  },

  setSelectedId(id) {
    set({ selectedId: id });
  },

  loadGraph(nodes: GraphNode[], edges: PathGraphEdge[]) {
    nodes.forEach((n) => {
      const num = parseInt(n.id.replace("node_", ""), 10);
      if (!isNaN(num) && num >= nextNodeId) nextNodeId = num + 1;
    });
    edges.forEach((e) => {
      const num = parseInt(e.id.replace("edge_", ""), 10);
      if (!isNaN(num) && num >= nextEdgeId) nextEdgeId = num + 1;
    });
    set({ nodes, edges, selectedId: null, validationErrors: [], undoStack: [], redoStack: [] });
  },

  clearGraph() {
    get().pushHistory();
    set({ nodes: [], edges: [], selectedId: null, validationErrors: [] });
  },

  validate() {
    const errors: ValidationError[] = [];
    const { nodes, edges } = get();
    const pathIds = new Set<string>();

    for (const edge of edges) {
      const d = edge.data;
      if (!d) continue;

      if (pathIds.has(d.pathId)) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "pathId", message: `Duplicate path_id: ${d.pathId}`,
        });
      }
      pathIds.add(d.pathId);

      if (!d.pathId || d.pathId.trim() === "") {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "pathId", message: "path_id is required",
        });
      }
      if (d.priority < 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "priority", message: "priority must be >= 0",
        });
      }
      if (d.climate.temperature.min > d.climate.temperature.max) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "climate.temperature", message: "temperature min > max",
        });
      }
      if (d.climate.downfall.min > d.climate.downfall.max) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "climate.downfall", message: "downfall min > max",
        });
      }
      if (d.chunkRules.positiveProgressStep <= 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "chunkRules.positiveProgressStep", message: "positiveProgressStep must be > 0",
        });
      }
      if (d.chunkRules.negativeProgressStep <= 0) {
        errors.push({
          type: "error", targetId: edge.id, targetType: "edge",
          field: "chunkRules.negativeProgressStep", message: "negativeProgressStep must be > 0",
        });
      }
    }

    for (const node of nodes) {
      const hasEdge = edges.some((e) => e.source === node.id || e.target === node.id);
      if (!hasEdge) {
        errors.push({
          type: "warning", targetId: node.id, targetType: "node",
          message: `Isolated biome: ${node.data.biomeId}`,
        });
      }
    }

    set({ validationErrors: errors });
    return errors.filter((e) => e.type === "error").length === 0;
  },

  clearValidation() {
    set({ validationErrors: [] });
  },

  getSelectedEdge() {
    const { selectedId, edges } = get();
    if (!selectedId) return undefined;
    return edges.find((e) => e.id === selectedId);
  },

  getConnectedEdges(nodeId) {
    return get().edges.filter((e) => e.source === nodeId || e.target === nodeId);
  },
}));
