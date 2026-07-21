import { useState } from "react";
import type { PlantSpawnRules } from "../../../model/types";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  spawnRules: PlantSpawnRules;
  onChange: (rules: PlantSpawnRules) => void;
}

const COMMON_BLOCKS = [
  "minecraft:grass_block",
  "minecraft:dirt",
  "minecraft:coarse_dirt",
  "minecraft:rooted_dirt",
  "minecraft:podzol",
  "minecraft:mycelium",
  "minecraft:sand",
  "minecraft:red_sand",
  "minecraft:gravel",
  "minecraft:stone",
  "minecraft:deepslate",
  "minecraft:netherrack",
  "minecraft:crimson_nylium",
  "minecraft:warped_nylium",
  "minecraft:end_stone",
  "minecraft:moss_block",
  "minecraft:mud",
  "minecraft:clay",
];

export function SpawnRulesEditor({ spawnRules, onChange }: Props) {
  const { t } = useT();
  const [collapsed, setCollapsed] = useState(true);
  const [newBlock, setNewBlock] = useState("");

  const setField = <K extends keyof PlantSpawnRules>(field: K, value: PlantSpawnRules[K]) => {
    onChange({ ...spawnRules, [field]: value });
  };

  const addBlock = () => {
    const block = newBlock.trim();
    if (block && !spawnRules.allowedBaseBlocks.includes(block)) {
      onChange({
        ...spawnRules,
        allowedBaseBlocks: [...spawnRules.allowedBaseBlocks, block],
      });
    }
    setNewBlock("");
  };

  const removeBlock = (idx: number) => {
    onChange({
      ...spawnRules,
      allowedBaseBlocks: spawnRules.allowedBaseBlocks.filter((_, i) => i !== idx),
    });
  };

  return (
    <div style={{ border: "1px solid #333", borderRadius: 6, padding: "6px 8px", background: "#1a1a30" }}>
      <div
        onClick={() => setCollapsed(!collapsed)}
        style={{
          cursor: "pointer",
          fontSize: 11,
          color: "#aaa",
          display: "flex",
          justifyContent: "space-between",
          marginBottom: collapsed ? 0 : 6,
        }}
      >
        <span>{collapsed ? "▶" : "▼"} {t("plant.spawnRules")}</span>
        <span style={{ fontSize: 9, color: "#666" }}>
          {t("plant.spawnSummary")
            .replace("{sky}", spawnRules.requireSky ? "✓" : "✗")
            .replace("{blocks}", String(spawnRules.allowedBaseBlocks.length))}
        </span>
      </div>

      {!collapsed && (
        <>
          <div style={{ marginBottom: 4 }}>
            <label style={{ color: "#888", fontSize: 10 }}>{t("spawn.density")}</label>
            <input
              type="number"
              value={spawnRules.maxLocalDensity}
              onChange={(e) => setField("maxLocalDensity", parseInt(e.target.value) || 1)}
              className="prop-input"
              style={{ width: "100%", fontSize: 10, padding: "2px 4px" }}
              min={1}
            />
          </div>

          <div style={{ marginBottom: 4 }}>
            <label style={{ color: "#888", fontSize: 10, display: "flex", alignItems: "center", gap: 6 }}>
              <input
                type="checkbox"
                checked={spawnRules.requireSky}
                onChange={(e) => setField("requireSky", e.target.checked)}
              />
              {t("spawn.requireSky")}
            </label>
          </div>

          <div>
            <label style={{ color: "#888", fontSize: 10 }}>{t("spawn.allowedBase")}</label>
            <div style={{ maxHeight: 100, overflowY: "auto", marginBottom: 4 }}>
              {spawnRules.allowedBaseBlocks.map((block, idx) => (
                <div
                  key={idx}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    padding: "1px 4px",
                    fontSize: 10,
                    fontFamily: "monospace",
                    color: "#ccc",
                    borderBottom: "1px solid #2a2a3e",
                  }}
                >
                  <span>{block}</span>
                  <button
                    onClick={() => removeBlock(idx)}
                    style={{
                      background: "none",
                      border: "none",
                      color: "#ef5350",
                      cursor: "pointer",
                      fontSize: 12,
                    }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
            <div style={{ display: "flex", gap: 4 }}>
              <input
                type="text"
                value={newBlock}
                onChange={(e) => setNewBlock(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && addBlock()}
                placeholder={t("spawn.blockPlaceholder")}
                className="prop-input mono"
                style={{ flex: 1, fontSize: 10, padding: "2px 4px" }}
                list="common-blocks"
              />
              <button
                onClick={addBlock}
                style={{
                  padding: "2px 8px",
                  background: "#2e7d32",
                  color: "#fff",
                  border: "none",
                  borderRadius: 4,
                  cursor: "pointer",
                  fontSize: 10,
                }}
              >
                {t("spawn.addBlock")}
              </button>
              <datalist id="common-blocks">
                {COMMON_BLOCKS.map((id) => (
                  <option key={id} value={id} />
                ))}
              </datalist>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
