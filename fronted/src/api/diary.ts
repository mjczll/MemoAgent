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
