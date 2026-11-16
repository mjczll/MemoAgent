import type { GraphData } from "@/types";

/** 图谱概览统计（纯展示统计，不算织网——织网规则已收敛到后端）。 */
export function graphStats(graph: GraphData): {
  nodes: number;
  edges: number;
  byType: Record<string, number>;
} {
  const byType: Record<string, number> = { diary: 0, experience: 0, knowledge: 0, domain: 0 };
  graph.nodes.forEach((n) => {
    byType[n.type] = (byType[n.type] ?? 0) + 1;
  });
  return { nodes: graph.nodes.length, edges: graph.edges.length, byType };
}
