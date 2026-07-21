import { useState, useMemo } from "react";
import type { PlantDefinition } from "../../../model/types";
import { defaultPlant } from "../../../model/defaults";
import { SpawnRulesEditor } from "./SpawnRulesEditor";
import { useT } from "../../../i18n/I18nContext";

interface Props {
  plants: PlantDefinition[];
  onAdd: (plant: PlantDefinition) => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, patch: Partial<PlantDefinition>) => void;
}

const CATEGORIES = ["ground_cover", "flower", "sapling", "mushroom", "tree", "vine", "crop"];
const COMMON_PLANTS = [
  "minecraft:short_grass",
  "minecraft:tall_grass",
  "minecraft:fern",
  "minecraft:large_fern",
  "minecraft:poppy",
  "minecraft:dandelion",
  "minecraft:blue_orchid",
  "minecraft:allium",
  "minecraft:azure_bluet",
  "minecraft:red_tulip",
  "minecraft:orange_tulip",
  "minecraft:white_tulip",
  "minecraft:pink_tulip",
  "minecraft:oxeye_daisy",
  "minecraft:cornflower",
  "minecraft:lily_of_the_valley",
  "minecraft:wither_rose",
  "minecraft:sunflower",
  "minecraft:lilac",
  "minecraft:rose_bush",
  "minecraft:peony",
  "minecraft:brown_mushroom",
  "minecraft:red_mushroom",
  "minecraft:oak_sapling",
  "minecraft:birch_sapling",
  "minecraft:spruce_sapling",
  "minecraft:jungle_sapling",
  "minecraft:acacia_sapling",
  "minecraft:dark_oak_sapling",
  "minecraft:cherry_sapling",
  "minecraft:mangrove_propagule",
  "minecraft:dead_bush",
  "minecraft:cactus",
  "minecraft:sugar_cane",
  "minecraft:bamboo",
  "minecraft:vine",
  "minecraft:lily_pad",
];

function groupPlantsByCategory(): Map<string, string[]> {
  const groups = new Map<string, string[]>();
  for (const id of COMMON_PLANTS) {
    const info = defaultPlant(id);
    const cat = info.category;
    if (!groups.has(cat)) groups.set(cat, []);
    groups.get(cat)!.push(id);
  }
  return groups;
}

export function PlantTableEditor({ plants, onAdd, onRemove, onUpdate }: Props) {
  const { t } = useT();
  const [collapsed, setCollapsed] = useState(false);
  const [expandedPlant, setExpandedPlant] = useState<number | null>(null);
  const [quickAddKey, setQuickAddKey] = useState(0);

  const totalWeight = plants.reduce((sum, p) => sum + p.weight, 0);
  const plantGroups = useMemo(() => groupPlantsByCategory(), []);

  const handleQuickAdd = (plantId: string) => {
    const plant = defaultPlant(plantId);
    onAdd(plant);
    setExpandedPlant(plants.length);
    setQuickAddKey((k) => k + 1);
  };

  return (
    <div className="prop-section">
      <div
        className="prop-section-title"
        onClick={() => setCollapsed(!collapsed)}
        style={{ cursor: "pointer", display: "flex", justifyContent: "space-between" }}
      >
        <span>{collapsed ? "▶" : "▼"} {t("path.plants")} ({plants.length})</span>
        <span style={{ fontSize: 10, color: "#888" }}>{t("path.totalWeight").replace("{total}", String(totalWeight))}</span>
      </div>

      {!collapsed && (
        <>
          <div style={{ overflowX: "auto", marginBottom: 8 }}>
            <table className="plant-table" style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
              <thead>
                <tr style={{ color: "#888", textAlign: "left" }}>
                  <th style={{ padding: "3px 4px", width: 28 }}>{t("plant.headerIndex")}</th>
                  <th style={{ padding: "3px 4px" }}>{t("plant.headerId")}</th>
                  <th style={{ padding: "3px 4px", width: 50 }}>{t("plant.headerWeight")}</th>
                  <th style={{ padding: "3px 4px", width: 40 }}>{t("plant.headerPoints")}</th>
                  <th style={{ padding: "3px 4px", width: 50 }}>{t("plant.headerAge")}</th>
                  <th style={{ padding: "3px 4px", width: 28 }}>{t("plant.headerActions")}</th>
                </tr>
              </thead>
              <tbody>
                {plants.map((plant, idx) => (
                  <PlantRow
                    key={idx}
                    plant={plant}
                    index={idx}
                    isExpanded={expandedPlant === idx}
                    onToggleExpand={() =>
                      setExpandedPlant(expandedPlant === idx ? null : idx)
                    }
                    onUpdate={(patch) => onUpdate(idx, patch)}
                    onRemove={() => {
                      onRemove(idx);
                      setExpandedPlant(expandedPlant === idx ? null : expandedPlant);
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {plants.length === 0 && (
            <div style={{ color: "#666", fontSize: 12, textAlign: "center", padding: 8 }}>
              {t("path.noPlants")}
            </div>
          )}

          <div style={{ marginBottom: 6 }}>
            <select
              key={quickAddKey}
              defaultValue=""
              onChange={(e) => {
                if (e.target.value) handleQuickAdd(e.target.value);
              }}
              style={{
                width: "100%",
                padding: "6px 8px",
                background: "#2a2a3e",
                color: "#ddd",
                border: "1px solid #444",
                borderRadius: 6,
                fontSize: 11,
                cursor: "pointer",
              }}
            >
              <option value="" disabled>
                {t("plant.quickAdd")}...
              </option>
              {Array.from(plantGroups.entries()).map(([cat, ids]) => (
                <optgroup key={cat} label={cat}>
                  {ids.map((id) => (
                    <option key={id} value={id}>
                      {id.replace("minecraft:", "")}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </div>

          <button
            onClick={() => {
              const plant = defaultPlant();
              onAdd(plant);
              setExpandedPlant(plants.length);
            }}
            style={{
              width: "100%",
              padding: "6px",
              background: "#2e7d32",
              color: "#fff",
              border: "none",
              borderRadius: 6,
              cursor: "pointer",
              fontSize: 12,
            }}
          >
            {t("plant.addCustom")}
          </button>
        </>
      )}
    </div>
  );
}

interface PlantRowProps {
  plant: PlantDefinition;
  index: number;
  isExpanded: boolean;
  onToggleExpand: () => void;
  onUpdate: (patch: Partial<PlantDefinition>) => void;
  onRemove: () => void;
}

function PlantRow({ plant, index, isExpanded, onToggleExpand, onUpdate, onRemove }: PlantRowProps) {
  const { t } = useT();
  return (
    <>
      <tr
        style={{
          borderBottom: "1px solid #2a2a3e",
          background: isExpanded ? "#252540" : "transparent",
        }}
      >
        <td style={{ padding: "3px 4px", color: "#666", cursor: "pointer" }} onClick={onToggleExpand}>
          {isExpanded ? "▼" : "▶"} {index + 1}
        </td>
        <td style={{ padding: "3px 4px" }}>
          <input
            type="text"
            value={plant.plantId}
            onChange={(e) => onUpdate({ plantId: e.target.value })}
            className="prop-input mono"
            style={{ width: "100%", fontSize: 10, padding: "2px 4px" }}
            list="common-plants"
          />
        </td>
        <td style={{ padding: "3px 4px" }}>
          <input
            type="number"
            value={plant.weight}
            onChange={(e) => onUpdate({ weight: parseInt(e.target.value) || 0 })}
            className="prop-input"
            style={{ width: 46, fontSize: 10, padding: "2px 4px" }}
            min={1}
          />
        </td>
        <td style={{ padding: "3px 4px" }}>
          <input
            type="number"
            value={plant.pointValue}
            onChange={(e) => onUpdate({ pointValue: parseInt(e.target.value) || 0 })}
            className="prop-input"
            style={{ width: 38, fontSize: 10, padding: "2px 4px" }}
            min={0}
          />
        </td>
        <td style={{ padding: "3px 4px" }}>
          <input
            type="number"
            value={plant.maxAgeTicks}
            onChange={(e) => onUpdate({ maxAgeTicks: parseInt(e.target.value) || 0 })}
            className="prop-input"
            style={{ width: 50, fontSize: 10, padding: "2px 4px" }}
            min={1}
            step={1000}
            title="ticks (24000 = 1 game day)"
          />
        </td>
        <td style={{ padding: "3px 4px" }}>
          <button
            onClick={onRemove}
            style={{
              background: "none",
              border: "none",
              color: "#ef5350",
              cursor: "pointer",
              fontSize: 14,
              padding: 0,
            }}
            title={t("plant.removeHint")}
          >
            ✕
          </button>
        </td>
      </tr>
      {isExpanded && (
        <tr>
          <td colSpan={6} style={{ padding: "4px 8px 8px", background: "#1e1e32" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6, fontSize: 11 }}>
              <div>
                <label style={{ color: "#888", fontSize: 10 }}>{t("plant.category")}</label>
                <input
                  type="text"
                  value={plant.category}
                  onChange={(e) => onUpdate({ category: e.target.value })}
                  className="prop-input"
                  style={{ width: "100%", fontSize: 10, padding: "2px 4px" }}
                  list="categories"
                />
              </div>
              <div>
                <label style={{ color: "#888", fontSize: 10 }}>{t("plant.maxAgeTicks")}</label>
                <input
                  type="number"
                  value={plant.maxAgeTicks}
                  onChange={(e) => onUpdate({ maxAgeTicks: parseInt(e.target.value) || 0 })}
                  className="prop-input"
                  style={{ width: "100%", fontSize: 10, padding: "2px 4px" }}
                  min={1}
                  step={6000}
                />
              </div>
            </div>
            <div style={{ marginTop: 6 }}>
              <SpawnRulesEditor
                spawnRules={plant.spawnRules}
                onChange={(spawnRules) => onUpdate({ spawnRules })}
              />
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

// datalist for common plants
export function PlantDatalists() {
  return (
    <>
      <datalist id="common-plants">
        {COMMON_PLANTS.map((id) => (
          <option key={id} value={id} />
        ))}
      </datalist>
      <datalist id="categories">
        {CATEGORIES.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>
    </>
  );
}
