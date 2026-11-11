import { request } from "./http";

export type HitKind = "diary" | "experience" | "knowledge";

export interface SearchHit {
  kind: HitKind;
  id: number;
  title: string;
  excerpt: string;
  score: number;
}

export interface SearchResult {
  hits: SearchHit[];
  available: boolean;
  notice: string | null;
}

/** 阶段 6：搜索页改走后端语义检索，不再用本地 mock 关键词匹配。 */
export function semanticSearch(
  q: string,
  kinds?: HitKind[],
  topK?: number,
): Promise<SearchResult> {
  const params = new URLSearchParams({ q });
  if (kinds?.length) params.set("kinds", kinds.join(","));
  if (topK) params.set("topK", String(topK));
  return request<SearchResult>(`/api/search?${params.toString()}`);
}
