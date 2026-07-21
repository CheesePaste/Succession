import { useState } from "react";
import type { PathGraphEdge, PathEdgeData } from "../../../model/types";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  edge: PathGraphEdge;
  onChange: (patch: Partial<PathEdgeData>) => void;
}

export function ChunkRulesEditor({ edge, onChange }: Props) {
  const { t } = useT();
  const { chunkRules: cr } = edge.data!;
  const [collapsed, setCollapsed] = useState(false);

  const setField = (field: string, value: number) => {
    onChange({ chunkRules: { ...cr, [field]: value } });
  };

  return (
    <div className="prop-section">
      <div
        className="prop-section-title"
        onClick={() => setCollapsed(!collapsed)}
        style={{ cursor: "pointer" }}
      >
        {collapsed ? "▶" : "▼"} {t("path.chunkRules")}
      </div>

      {!collapsed && (
        <>
          <div className="prop-row">
            <label>{t("path.positiveStep")}</label>
            <input
              type="number"
              value={cr.positiveProgressStep}
              onChange={(e) => setField("positiveProgressStep", parseFloat(e.target.value) || 0.1)}
              className="prop-input"
              style={{ width: 80 }}
              min={0.05}
              step={0.05}
            />
          </div>

          <div className="prop-row">
            <label>{t("path.negativeStep")}</label>
            <input
              type="number"
              value={cr.negativeProgressStep}
              onChange={(e) => setField("negativeProgressStep", parseFloat(e.target.value) || 0.1)}
              className="prop-input"
              style={{ width: 80 }}
              min={0.05}
              step={0.05}
            />
          </div>
        </>
      )}
    </div>
  );
}
