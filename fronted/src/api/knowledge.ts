import { request } from "./http";
import type { DiaryPage } from "./diary";
import type { Knowledge, Mastery } from "@/types";

export interface KnowledgeQuery {
  keyword?: string;
  domain?: string;
  mastery?: string;
  page?: number;
  size?: number;
}

export interface KnowledgePayload {
  title: string;
  content: string;
  category?: string;
  domain?: string;
  summary?: string;
  mastery?: Mastery;
  tags?: string[];
  relatedIds?: string[];
  sourceExperienceIds?: string[];
}

interface KnowledgeDto {
  id: number | string;
  title: string;
  category?: string;
  domain?: string;
  tags?: string[];
  summary?: string;
  content?: string;
  relatedIds?: Array<number | string>;
  sourceExperienceIds?: Array<number | string>;
  mastery?: Mastery;
  visibility?: Knowledge["visibility"];
  updatedAt: string;
}

export function toKnowledge(dto: KnowledgeDto): Knowledge {
  return {
    id: String(dto.id),
    title: dto.title,
    category: dto.category || "未分类",
    domain: dto.domain ?? "项目开发",
    tags: dto.tags ?? [],
    summary: dto.summary ?? "",
    content: dto.content ?? "",
    relatedIds: (dto.relatedIds ?? []).map(String),
    sourceExperienceIds: (dto.sourceExperienceIds ?? []).map(String),
    mastery: dto.mastery ?? "待补充",
    visibility: dto.visibility ?? "private",
    updatedAt: dto.updatedAt,
  };
}

export function listKnowledge(query: KnowledgeQuery = {}): Promise<DiaryPage<Knowledge>> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set("keyword", query.keyword.trim());
  if (query.domain && query.domain !== "all") params.set("domain", query.domain);
  if (query.mastery && query.mastery !== "all") params.set("mastery", query.mastery);
  params.set("page", String(query.page ?? 1));
  params.set("size", String(query.size ?? 100));
  return request<DiaryPage<KnowledgeDto>>(`/api/knowledge?${params.toString()}`).then((page) => ({
    records: page.records.map(toKnowledge),
    total: page.total,
    page: page.page,
    size: page.size,
  }));
}

export function getKnowledge(id: string): Promise<Knowledge> {
  return request<KnowledgeDto>(`/api/knowledge/${id}`).then(toKnowledge);
}

export function createKnowledge(payload: KnowledgePayload): Promise<Knowledge> {
  return request<KnowledgeDto>("/api/knowledge", {
    method: "POST",
    body: JSON.stringify(toBody(payload)),
  }).then(toKnowledge);
}

export function updateKnowledge(id: string, payload: KnowledgePayload): Promise<Knowledge> {
  return request<KnowledgeDto>(`/api/knowledge/${id}`, {
    method: "PUT",
    body: JSON.stringify(toBody(payload)),
  }).then(toKnowledge);
}

export function deleteKnowledge(id: string): Promise<void> {
  return request<null>(`/api/knowledge/${id}`, { method: "DELETE" }).then(() => undefined);
}

function toBody(payload: KnowledgePayload) {
  return {
    ...payload,
    relatedIds: payload.relatedIds?.map(Number),
    sourceExperienceIds: payload.sourceExperienceIds?.map(Number),
  };
}
