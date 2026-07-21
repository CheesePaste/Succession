import { useState } from "react";
import { SuccessionPathPage } from "./SuccessionPathPage";
import { BiomeRulesPage } from "./BiomeRulesPage";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { I18nProvider, useT } from "./i18n/I18nContext";
import "./App.css";

type Page = "paths" | "biomes";

function TabBar({ page, onPageChange }: { page: Page; onPageChange: (p: Page) => void }) {
  const { t } = useT();
  return (
    <div
      style={{
        display: "flex",
        background: "#0f0f20",
        borderBottom: "1px solid #333",
        padding: "0 12px",
      }}
    >
      <TabButton
        active={page === "paths"}
        onClick={() => onPageChange("paths")}
      >
        {t("tab.paths")}
      </TabButton>
      <TabButton
        active={page === "biomes"}
        onClick={() => onPageChange("biomes")}
      >
        {t("tab.biomes")}
      </TabButton>
    </div>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: "10px 20px",
        background: "none",
        border: "none",
        borderBottom: active ? "2px solid #4caf50" : "2px solid transparent",
        color: active ? "#4caf50" : "#888",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: active ? "bold" : "normal",
        transition: "all 0.15s",
      }}
    >
      {children}
    </button>
  );
}

function AppContent() {
  const [page, setPage] = useState<Page>("paths");

  return (
    <div className="app-container">
      <TabBar page={page} onPageChange={setPage} />
      {page === "paths" ? <SuccessionPathPage /> : <BiomeRulesPage />}
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <I18nProvider>
        <AppContent />
      </I18nProvider>
    </ErrorBoundary>
  );
}
