import { useEffect, useState } from "react";
import { KnowledgeWorkbench } from "../features/workbench/KnowledgeWorkbench";
import { loadWorkbenchBootstrapView, type WorkbenchBootstrapView } from "./bootstrap";
import { workbenchFixture } from "./fixtures";

export function App() {
  const [bootstrap, setBootstrap] = useState<WorkbenchBootstrapView>({
    status: "fixture-fallback",
    statusLabel: "离线预览",
    data: workbenchFixture,
  });

  useEffect(() => {
    let mounted = true;
    void loadWorkbenchBootstrapView(window.fetch.bind(window)).then((nextBootstrap) => {
      if (mounted) {
        setBootstrap(nextBootstrap);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  return <KnowledgeWorkbench backendStatusLabel={bootstrap.statusLabel} data={bootstrap.data} />;
}
