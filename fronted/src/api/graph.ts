import { request } from "./http";
import type { GraphData } from "@/types";

/** 全局织网：domain 传领域名则返回该领域子图，null/不传返回全图 */
export function fetchGraph(domain?: string | null): Promise<GraphData> {
  const query = domain ? `?domain=${encodeURIComponent(domain)}` : "";
  return request<GraphData>(`/api/graph${query}`);
}

/** 局部关系图：以指定类型的实体为中心（三种实体共用主键，必须带 type），depth 1~3 */
export function fetchLocalGraph(id: string, type: "diary" | "experience" | "knowledge", depth: number): Promise<GraphData> {
  return request<GraphData>(`/api/graph/local/${id}?type=${type}&depth=${depth}`);
}
