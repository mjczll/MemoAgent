/**
 * useGraphEngine — Vue composable，管理 GraphEngine 生命周期
 */
import { onBeforeUnmount, ref, shallowRef, type Ref } from "vue";
import {
  GraphEngine,
  type GraphNodeInput,
  type GraphEdgeInput,
  type ForcesConfig,
  type DisplayConfig,
} from "@/graph/GraphEngine";

export interface UseGraphEngineOptions {
  onHover?: (id: string | null) => void;
  onSelect?: (id: string | null) => void;
  onStats?: (s: { nodes: number; edges: number }) => void;
}

export function useGraphEngine(opts: UseGraphEngineOptions = {}) {
  const engine = shallowRef<GraphEngine | null>(null);
  const ready = ref(false);
  const liveStats = ref({ nodes: 0, edges: 0 });

  async function mount(container: HTMLElement): Promise<void> {
    const e = new GraphEngine({
      onHover: opts.onHover ?? (() => {}),
      onSelect: opts.onSelect ?? (() => {}),
      onStats: (s) => {
        liveStats.value = s;
        opts.onStats?.(s);
      },
    });
    await e.mount(container);
    engine.value = e;
    ready.value = true;
  }

  function setData(nodes: GraphNodeInput[], edges: GraphEdgeInput[]): void {
    engine.value?.setData(nodes, edges);
  }

  function setForces(f: ForcesConfig): void {
    engine.value?.setForces(f);
  }

  function setDisplay(d: DisplayConfig): void {
    engine.value?.setDisplay(d);
  }

  function select(id: string | null): void {
    engine.value?.select(id);
  }

  function search(q: string): void {
    engine.value?.search(q);
  }

  function fitView(pad?: number): void {
    engine.value?.fitView(pad);
  }

  function zoomBy(factor: number, cx?: number, cy?: number): void {
    engine.value?.zoomBy(factor, cx, cy);
  }

  function replayGrowth(): void {
    engine.value?.replayGrowth();
  }

  function getSelectedNode() {
    return engine.value?.getSelectedNode() ?? null;
  }

  onBeforeUnmount(() => {
    engine.value?.destroy();
    engine.value = null;
  });

  return {
    engine,
    ready,
    liveStats,
    mount,
    setData,
    setForces,
    setDisplay,
    select,
    search,
    fitView,
    zoomBy,
    replayGrowth,
    getSelectedNode,
  };
}
