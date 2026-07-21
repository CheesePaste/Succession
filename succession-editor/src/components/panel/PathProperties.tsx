import { useEditorStore } from "../../store/editorStore";
import type { PathGraphEdge } from "../../model/types";
import { PathIdentityEditor } from "./editors/PathIdentityEditor";
import { ClimateEditor } from "./editors/ClimateEditor";
import { ChunkRulesEditor } from "./editors/ChunkRulesEditor";
import { useT } from "../../i18n/I18nContext";

interface Props {
  edge: PathGraphEdge;
}

export function PathProperties({ edge }: Props) {
  const { t } = useT();
  const updateEdgeData = useEditorStore((s) => s.updateEdgeData);
  const removeEdge = useEditorStore((s) => s.removeEdge);

  return (
    <div style={{ padding: 12 }}>
      <PathIdentityEditor edge={edge} onChange={(p) => updateEdgeData(edge.id, p)} />
      <ClimateEditor edge={edge} onChange={(p) => updateEdgeData(edge.id, p)} />
      <ChunkRulesEditor edge={edge} onChange={(p) => updateEdgeData(edge.id, p)} />

      <div className="prop-section">
        <button
          onClick={() => removeEdge(edge.id)}
          style={{
            width: "100%",
            padding: "8px",
            background: "#c62828",
            color: "#fff",
            border: "none",
            borderRadius: 6,
            cursor: "pointer",
            fontSize: 13,
          }}
        >
          {t("path.removePath")}
        </button>
      </div>
    </div>
  );
}
