import { request } from "./http";
import type { TodoItem } from "@/types";

/** 待办通知：查询式动态计算（草稿待确认 + 每周回顾待生成） */
export function fetchTodos(): Promise<TodoItem[]> {
  return request<TodoItem[]>("/api/todos");
}
