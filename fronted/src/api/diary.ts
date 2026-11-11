import { request } from "./http";
import type { Diary, DiaryKind, Visibility } from "@/types";

export interface DiaryPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface DiaryQuery {
  keyword?: string;
  kind?: string;
  sort?: "asc" | "desc";
  page?: number;
  size?: number;
}

export interface DiaryPayload {
  title: string;
  content: string;
  summary?: string;
  date: string;
  kind: DiaryKind | string;
  origin?: Diary["origin"];
  tags?: string[];
}

interface DiaryDto {
  id: number | string;
  title: string;
  date: string;
  kind: string;
  kindId?: number | string;
  origin?: string;
  summary?: string;
  content?: string;
  tags?: string[];
  visibility?: Visibility;
  experienceCount?: number;
  knowledgeCount?: number;
  experienceIds?: Array<number | string>;
  knowledgeIds?: Array<number | string>;
  createdAt: string;
  updatedAt?: string;
}

export function toDiary(dto: DiaryDto): Diary {
  const experienceIds = (dto.experienceIds ?? []).map(String);
  const knowledgeIds = (dto.knowledgeIds ?? []).map(String);
  return {
    id: String(dto.id),
    title: dto.title,
    date: dto.date,
    kind: dto.kind as DiaryKind,
    kindId: dto.kindId != null ? String(dto.kindId) : undefined,
    tags: dto.tags ?? [],
    summary: dto.summary ?? "",
    content: dto.content ?? "",
    visibility: dto.visibility ?? "private",
    experienceIds,
    knowledgeIds,
    experienceCount: dto.experienceCount ?? experienceIds.length,
    knowledgeCount: dto.knowledgeCount ?? knowledgeIds.length,
    origin: dto.origin === "interview" ? "interview" : "manual",
    createdAt: dto.createdAt,
  };
}

export function listDiaries(query: DiaryQuery = {}): Promise<DiaryPage<Diary>> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set("keyword", query.keyword.trim());
  if (query.kind && query.kind !== "all") params.set("kind", query.kind);
  if (query.sort) params.set("sort", query.sort);
  params.set("page", String(query.page ?? 1));
  params.set("size", String(query.size ?? 100));
  return request<DiaryPage<DiaryDto>>(`/api/diaries?${params.toString()}`).then((page) => ({
    records: page.records.map(toDiary),
    total: page.total,
    page: page.page,
    size: page.size,
  }));
}

export function getDiary(id: string): Promise<Diary> {
  return request<DiaryDto>(`/api/diaries/${id}`).then(toDiary);
}

export function createDiary(payload: DiaryPayload): Promise<Diary> {
  return request<DiaryDto>("/api/diaries", {
    method: "POST",
    body: JSON.stringify(payload),
  }).then(toDiary);
}

export function updateDiary(id: string, payload: DiaryPayload): Promise<Diary> {
  return request<DiaryDto>(`/api/diaries/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  }).then(toDiary);
}

export function deleteDiary(id: string): Promise<void> {
  return request<null>(`/api/diaries/${id}`, { method: "DELETE" }).then(() => undefined);
}

export interface KnowledgeCandidate {
  title: string;
  domain?: string;
  category?: string;
  summary?: string;
  content?: string;
  tags?: string[];
}

export interface ExperienceCandidate {
  title: string;
  problem: string;
  cause?: string;
  solution?: string;
  lesson?: string;
  domain?: string;
  tags?: string[];
  knowledge?: KnowledgeCandidate | null;
}

export interface DiaryGeneration {
  title: string;
  summary: string;
  content: string;
  kind: string;
  tags: string[];
  experiences: ExperienceCandidate[];
}

export function generateDiary(content: string): Promise<DiaryGeneration> {
  return request<DiaryGeneration>("/api/diaries/generate", {
    method: "POST",
    body: JSON.stringify({ content }),
  });
}

export function extractDiary(id: string): Promise<{ experiences: ExperienceCandidate[] }> {
  return request<{ experiences: ExperienceCandidate[] }>(`/api/diaries/${id}/extract`, { method: "POST" });
}

/** 手写日记保存后由后台自动提取的候选，需用户确认才会变成经验。 */
export interface DiaryCandidate {
  id: number;
  diaryId: number;
  title: string;
  problem: string;
  cause?: string | null;
  solution?: string | null;
  lesson?: string | null;
  domain: string;
  tags: string[];
  knowledge?: {
    title: string;
    category?: string;
    domain?: string;
    summary?: string;
    content?: string;
    tags?: string[];
  } | null;
}

export function listDiaryCandidates(id: string | number): Promise<DiaryCandidate[]> {
  return request<DiaryCandidate[]>(`/api/diaries/${id}/candidates`);
}

export function acceptDiaryCandidate(id: number): Promise<number> {
  return request<number>(`/api/candidates/${id}/accept`, { method: "POST" });
}

export function ignoreDiaryCandidate(id: number): Promise<void> {
  return request<null>(`/api/candidates/${id}/ignore`, { method: "POST" }).then(() => undefined);
}
