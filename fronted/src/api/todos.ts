import { request } from "./http";
import type { TodoItem } from "@/types";

/** 待办通知：查询式动态计算（草稿待确认 + 每周回顾待生成） */
export function fetchTodos(): Promise<TodoItem[]> {
  return request<TodoItem[]>("/api/todos");
}

/**
 * 订阅待办变更（SSE，见开发步骤文档 18.2）。
 * 后端在草稿生成 / 提交 / 删除 / 回顾生成的事务提交后、以及每周回顾到点时
 * 广播 todos-changed；前端收到后拉一次全量，不再本地轮询。
 * EventSource 自带断线重连；返回值是关闭订阅的函数。
 */
export function subscribeTodos(onChanged: () => void): () => void {
  const source = new EventSource("/api/todos/stream");
  source.onmessage = () => onChanged();
  // onerror 不手动 close：EventSource 会按默认间隔自动重连
  return () => source.close();
}
