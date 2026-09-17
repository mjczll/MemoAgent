import { request } from "./http";

export interface StatsDto {
  diaries: number;
  experiences: number;
  knowledge: number;
  mastered: number;
  masteryRate: number;
  domains: number;
  interviewOrigin: number;
  linkCount: number;
}

export function getStats(): Promise<StatsDto> {
  return request<StatsDto>("/api/stats");
}
