import type { GraphData, GraphEdge, GraphNode } from "@/types";
import { DOMAINS } from "@/mock/domains";
import type { LibraryData } from "./search";

/** 日记的领域取自首个标签（与图谱节点归属保持一致） */
export function diaryDomain(tags: string[]): string {
  return tags[0] ?? "项目开发";
}

/**
 * 织网：把日记 → 经验 → 知识 → 领域 连成一张知识图谱。
 * 边语义：提炼（日记→经验）、沉淀（经验→知识）、关联（日记→知识）、相关（知识↔知识）、归属（实体→领域）
 *
 * @param domain 传领域名则只看该专业领域（全局织网传 null）
 */
export function buildGraph(
  data: LibraryData,
  domain: string | null = null,
): GraphData {
  const nodes: GraphNode[] = [];
  const edges: GraphEdge[] = [];
  const nodeIds = new Set<string>();
  const edgeKeys = new Set<string>();

  const inDomain = (name: string) => !domain || name === domain;

  const diaries = data.diaries.filter((d) => inDomain(diaryDomain(d.tags)));
  const experiences = data.experiences.filter((e) => inDomain(e.domain));
  const knowledge = data.knowledge.filter((k) => inDomain(k.domain));

  const pushNode = (node: GraphNode) => {
    if (nodeIds.has(node.id)) return;
    nodeIds.add(node.id);
    nodes.push(node);
  };

  const pushEdge = (source: string, target: string, label: string) => {
    if (!nodeIds.has(source) || !nodeIds.has(target)) return;
    const key = `${source}|${target}|${label}`;
    if (edgeKeys.has(key)) return;
    edgeKeys.add(key);
    edges.push({ id: `ge-${edges.length + 1}`, source, target, label });
  };

  // 领域（中心节点）
  DOMAINS.forEach((d) => {
    const count =
      knowledge.filter((k) => k.domain === d.name).length +
      experiences.filter((e) => e.domain === d.name).length;
    if (!count) return;
    pushNode({
      id: `domain:${d.name}`,
      label: d.name,
      type: "domain",
      domain: d.name,
      refId: d.name,
      visibility: "private",
      weight: 8 + count * 1.6,
    });
  });

  knowledge.forEach((k) => {
    pushNode({
      id: k.id,
      label: k.title,
      type: "knowledge",
      domain: k.domain,
      refId: k.id,
      visibility: k.visibility,
      weight: 4 + k.relatedIds.length * 0.9 + k.sourceExperienceIds.length * 0.9,
      updatedAt: k.updatedAt,
    });
  });

  experiences.forEach((e) => {
    pushNode({
      id: e.id,
      label: e.title,
      type: "experience",
      domain: e.domain,
      refId: e.id,
      visibility: e.visibility,
      weight: 5 + e.knowledgeIds.length * 0.9,
      updatedAt: e.createdAt,
    });
  });

  diaries.forEach((d) => {
    pushNode({
      id: d.id,
      label: d.title,
      type: "diary",
      domain: d.tags[0] ?? "项目开发",
      refId: d.id,
      visibility: d.visibility,
      weight: 3.5 + (d.experienceIds.length + d.knowledgeIds.length) * 0.7,
      updatedAt: d.createdAt,
    });
  });

  // 日记 → 经验：提炼
  diaries.forEach((d) => {
    d.experienceIds.forEach((eid) => pushEdge(d.id, eid, "提炼"));
  });

  // 经验 → 知识：沉淀
  experiences.forEach((e) => {
    e.knowledgeIds.forEach((kid) => pushEdge(e.id, kid, "沉淀"));
  });

  // 日记 → 知识：关联
  diaries.forEach((d) => {
    d.knowledgeIds.forEach((kid) => pushEdge(d.id, kid, "关联"));
  });

  // 知识 ↔ 知识：相关
  knowledge.forEach((k) => {
    k.relatedIds.forEach((kid) => {
      if (!nodeIds.has(kid)) return;
      pushEdge(k.id, kid, "相关");
    });
  });

  // 实体 → 领域：归属
  [...knowledge, ...experiences].forEach((item) => {
    pushEdge(item.id, `domain:${item.domain}`, "归属");
  });

  return { nodes, edges };
}

/** 图谱概览统计 */
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

/**
 * 局部关系图：以 centerId 为起点，BFS 获取 depth 跳邻居
 */
export function buildLocalGraph(
  data: LibraryData,
  centerId: string,
  depth: number = 2,
): GraphData {
  const full = buildGraph(data, null);
  const adj = new Map<string, Set<string>>();
  full.nodes.forEach((n) => adj.set(n.id, new Set()));
  full.edges.forEach((e) => {
    adj.get(e.source)?.add(e.target);
    adj.get(e.target)?.add(e.source);
  });

  const visited = new Map<string, number>();
  const queue: [string, number][] = [[centerId, 0]];
  visited.set(centerId, 0);

  while (queue.length > 0) {
    const [id, d] = queue.shift()!;
    if (d >= depth) continue;
    const neighbors = adj.get(id);
    if (!neighbors) continue;
    for (const nid of neighbors) {
      if (!visited.has(nid)) {
        visited.set(nid, d + 1);
        queue.push([nid, d + 1]);
      }
    }
  }

  const nodeIds = new Set(visited.keys());
  return {
    nodes: full.nodes.filter((n) => nodeIds.has(n.id)),
    edges: full.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target)),
  };
}
