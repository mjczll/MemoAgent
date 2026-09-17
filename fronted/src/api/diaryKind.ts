import { request } from "./http";
import type { DiaryKindItem } from "@/types";

interface DiaryKindDto {
  id: number | string;
  name: string;
  sortOrder: number;
  isDefault: boolean;
  diaryCount?: number;
}

function toItem(dto: DiaryKindDto): DiaryKindItem {
  return {
    id: String(dto.id),
    name: dto.name,
    sortOrder: dto.sortOrder,
    isDefault: Boolean(dto.isDefault),
    diaryCount: dto.diaryCount ?? 0,
  };
}

export function listDiaryKinds(): Promise<DiaryKindItem[]> {
  return request<DiaryKindDto[]>("/api/diary-kinds").then((rows) => rows.map(toItem));
}
