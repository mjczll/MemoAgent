import { request } from "./http";
import type { DiaryPage } from "./diary";
import type { Experience } from "@/types";

export interface ExperienceQuery {
  keyword?: string;
  domain?: string;
  page?: number;
  size?: number;
}

export interface KnowledgeDraftPayload {
  title: string;
  category?: string;
  domain?: string;
  summary?: string;
  content?: string;
  tags?: string[];
  relatedIds?: Array<number | string>;
}

export interface ExperienceCreatePayload {
  diaryId: string;
  title: string;
  problem: string;
  cause?: string;
  solution?: string;
  lesson?: string;
  domain?: string;
  tags?: string[];
  knowledgeIds?: string[];
  newKnowledge?: KnowledgeDraftPayload[];
}

export interface ExperienceUpdatePayload {
  title: string;
  problem: string;
  cause?: string;
  solution?: string;
  lesson?: string;
  domain?: string;
  tags?: string[];
  knowledgeIds?: string[];
}

interface ExperienceDto {
  id: number | string;
  title: string;
  problem?: string;
  cause?: string;
  solution?: string;
  lesson?: string;
  tags?: string[];
  domain?: string;
  diaryId?: number | string;
  diaryTitle?: string;
  knowledgeIds?: Array<number | string>;
  visibility?: Experience["visibility"];
  createdAt: string;
}

export function toExperience(dto: ExperienceDto): Experience {
  return {
    id: String(dto.id),
    title: dto.title,
    problem: dto.problem ?? "",
    cause: dto.cause ?? "",
    solution: dto.solution ?? "",
    lesson: dto.lesson ?? "",
    tags: dto.tags ?? [],
    domain: dto.domain ?? "项目开发",
    diaryId: dto.diaryId != null ? String(dto.diaryId) : "",
    knowledgeIds: (dto.knowledgeIds ?? []).map(String),
    visibility: dto.visibility ?? "private",
    createdAt: dto.createdAt,
  };
}

export function listExperiences(query: ExperienceQuery = {}): Promise<DiaryPage<Experience>> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set("keyword", query.keyword.trim());
  if (query.domain && query.domain !== "all") params.set("domain", query.domain);
  params.set("page", String(query.page ?? 1));
  params.set("size", String(query.size ?? 100));
  return request<DiaryPage<ExperienceDto>>(`/api/experiences?${params.toString()}`).then((page) => ({
    records: page.records.map(toExperience),
    total: page.total,
    page: page.page,
    size: page.size,
  }));
}

export function getExperience(id: string): Promise<Experience> {
  return request<ExperienceDto>(`/api/experiences/${id}`).then(toExperience);
}

export function createExperience(payload: ExperienceCreatePayload): Promise<Experience> {
  return request<ExperienceDto>("/api/experiences", {
    method: "POST",
    body: JSON.stringify({
      ...payload,
      diaryId: Number(payload.diaryId),
      knowledgeIds: (payload.knowledgeIds ?? []).map(Number),
      newKnowledge: (payload.newKnowledge ?? []).map((item) => ({
        ...item,
        relatedIds: (item.relatedIds ?? []).map(Number),
      })),
    }),
  }).then(toExperience);
}

export function updateExperience(id: string, payload: ExperienceUpdatePayload): Promise<Experience> {
  return request<ExperienceDto>(`/api/experiences/${id}`, {
    method: "PUT",
    body: JSON.stringify({
      ...payload,
      knowledgeIds: payload.knowledgeIds?.map(Number),
    }),
  }).then(toExperience);
}
