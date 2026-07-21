import { useEditorStore } from "../../store/editorStore";
import { useT } from "../../i18n/I18nContext";

interface Props {
  nodeCount: number;
  edgeCount: number;
}

export function NoSelection({ nodeCount, edgeCount }: Props) {
  const { t } = useT();
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  const sourceBiomes = new Set(edges.map((e) => (nodes.find((n) => n.id === e.source)?.data as any)?.biomeId).filter(Boolean));
  const targetBiomes = new Set(edges.map((e) => (nodes.find((n) => n.id === e.target)?.data as any)?.biomeId).filter(Boolean));

  return (
    <div style={{ padding: 16 }}>
      <div style={{ marginBottom: 20 }}>
        <h3 style={{ margin: "0 0 12px 0", color: "#ccc", fontSize: 15 }}>{t("overview.title")}</h3>
        <div className="stat-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <StatBox label={t("overview.biomeNodes")} value={nodeCount} />
          <StatBox label={t("overview.paths")} value={edgeCount} />
          <StatBox label={t("overview.sourceBiomes")} value={sourceBiomes.size} />
          <StatBox label={t("overview.targetBiomes")} value={targetBiomes.size} />
        </div>
      </div>

      {nodes.length > 0 && (
        <div style={{ marginBottom: 20 }}>
          <h3 style={{ margin: "0 0 8px 0", color: "#ccc", fontSize: 15 }}>{t("overview.biomesOnCanvas")}</h3>
          <div style={{ maxHeight: 200, overflowY: "auto" }}>
            {nodes.filter((n) => n.data.type === "biome").map((node) => {
              const connEdges = edges.filter(
                (e) => e.source === node.id || e.target === node.id,
              );
              const hasOut = connEdges.some((e) => e.source === node.id);
              const hasIn = connEdges.some((e) => e.target === node.id);
              const d = node.data as any;
              const meta = d.biomeMeta;
              const biomeId: string = d.biomeId;
              return (
                <div
                  key={node.id}
                  style={{
                    padding: "4px 8px",
                    fontSize: 12,
                    color: "#aaa",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    borderBottom: "1px solid #2a2a3e",
                  }}
                >
                  <span>
                    {meta?.displayName ?? biomeId.replace("minecraft:", "")}
                    {meta && (
                      <span style={{ color: "#777", marginLeft: 6 }}>
                        🌡{(meta as any).defaultTemp.toFixed(1)}
                      </span>
                    )}
                  </span>
                  <span style={{ fontSize: 10, color: "#666" }}>
                    {hasOut ? "→" : ""} {hasIn ? "←" : ""}
                    {!hasOut && !hasIn ? t("overview.isolated") : ""}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {edges.length > 0 && (
        <div>
          <h3 style={{ margin: "0 0 8px 0", color: "#ccc", fontSize: 15 }}>{t("overview.pathsList")}</h3>
          <div style={{ maxHeight: 200, overflowY: "auto" }}>
            {edges.map((edge) => {
              const srcNode = nodes.find((n) => n.id === edge.source);
              const tgtNode = nodes.find((n) => n.id === edge.target);
              return (
                <div
                  key={edge.id}
                  style={{
                    padding: "4px 8px",
                    fontSize: 11,
                    color: "#aaa",
                    fontFamily: "monospace",
                    borderBottom: "1px solid #2a2a3e",
                  }}
                >
                  {((srcNode?.data as any)?.biomeId ?? "?").replace("minecraft:", "")} →{" "}
                  {((tgtNode?.data as any)?.biomeId ?? "?").replace("minecraft:", "")}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {nodes.length === 0 && (
        <div style={{ color: "#666", fontSize: 13, textAlign: "center", marginTop: 40 }}>
          {t("overview.emptyHint")}
        </div>
      )}
    </div>
  );
}

function StatBox({ label, value }: { label: string; value: number }) {
  return (
    <div
      style={{
        background: "#252540",
        borderRadius: 8,
        padding: "10px 12px",
        textAlign: "center",
      }}
    >
      <div style={{ fontSize: 22, fontWeight: "bold", color: "#fff" }}>{value}</div>
      <div style={{ fontSize: 10, color: "#888", marginTop: 2 }}>{label}</div>
    </div>
  );
}
