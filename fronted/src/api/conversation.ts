import { ApiError, request } from "./http";

export interface ConversationRef {
  type: "diary" | "experience" | "knowledge";
  id: number;
  title: string;
}

export interface ConversationLink {
  knowledgeId: number;
  title: string;
  choice?: "accept" | "skip" | null;
}

export interface ConversationSupplement {
  title: string;
  domain?: string;
  category?: string;
  summary?: string;
  content: string;
}

export interface ConversationPayload {
  choice?: "accept" | "skip" | null;
  ready?: boolean;
  supplement?: ConversationSupplement | null;
  refs?: ConversationRef[];
  links?: ConversationLink[];
}

export interface ConversationMessage {
  id: number;
  role: "user" | "assistant";
  kind: "interview" | "recall" | "supplement" | "association";
  text: string;
  payload?: ConversationPayload | null;
  createdAt: string;
}

export interface ConversationDraft {
  incident: boolean;
  title: string;
  date: string;
  kind: string;
  domain?: string;
  tags: string[];
  summary: string;
  content: string;
  problem: string;
  cause: string;
  solution: string;
  lesson: string;
}

export interface RecallHit {
  type: "diary" | "experience" | "knowledge";
  id: number;
  title: string;
  excerpt?: string;
}

export interface ConversationDetail {
  id: number;
  title: string;
  status: "active" | "drafted" | "committed";
  draft?: ConversationDraft | null;
  committedDiaryId?: number | null;
  updatedAt: string;
  messages: ConversationMessage[];
  related?: RecallHit[];
}

export interface ConversationListItem {
  id: number;
  title: string;
  status: "active" | "drafted" | "committed";
  updatedAt: string;
}

export interface CommitResult {
  diaryId?: number | null;
  experienceId?: number | null;
  knowledgeIds: number[];
}

export function listConversations(): Promise<ConversationListItem[]> {
  return request<ConversationListItem[]>("/api/conversations");
}

export function createConversation(opening?: string): Promise<ConversationDetail> {
  return request<ConversationDetail>("/api/conversations", {
    method: "POST",
    body: JSON.stringify({ opening: opening ?? "" }),
  });
}

export function getConversation(id: number | string): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/api/conversations/${id}`);
}

export function deleteConversation(id: number | string): Promise<void> {
  return request<null>(`/api/conversations/${id}`, { method: "DELETE" }).then(() => undefined);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

export async function streamConversationMessage(
  id: number | string,
  text: string,
  onToken: (chunk: string) => void,
  signal?: AbortSignal,
): Promise<ConversationDetail> {
  let response: Response;
  try {
    response = await fetch(`/api/conversations/${id}/messages/stream`, {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ text }),
      signal,
    });
  } catch (error) {
    if (isAbortError(error)) throw error;
    throw new ApiError(0, "无法连接后端，请确认服务已启动");
  }
  if (!response.ok || !response.body) {
    const raw = await response.text();
    let message = "回复失败";
    try {
      message = (JSON.parse(raw) as { message?: string }).message || message;
    } catch {
      /* 流式失败时不一定是 JSON */
    }
    throw new ApiError(response.status, message);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let doneDetail: ConversationDetail | null = null;
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) break;
    buffer += decoder.decode(chunk.value, { stream: true });
    const parts = buffer.split("\n\n");
    buffer = parts.pop() ?? "";
    for (const part of parts) {
      const parsed = parseSse(part);
      if (!parsed.data) continue;
      if (parsed.event === "token") {
        const payload = JSON.parse(parsed.data) as { text?: string };
        if (payload.text) onToken(payload.text);
      } else if (parsed.event === "done") {
        doneDetail = JSON.parse(parsed.data) as ConversationDetail;
      } else if (parsed.event === "error") {
        const payload = JSON.parse(parsed.data) as { message?: string };
        throw new ApiError(500, payload.message || "回复失败");
      }
    }
  }
  if (!doneDetail) throw new ApiError(500, "回复中断了，可以再发一次");
  return doneDetail;
}

function parseSse(block: string): { event: string; data: string } {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) data.push(line.slice(5).trim());
  }
  return { event, data: data.join("\n") };
}

export function sendConversationMessage(id: number | string, text: string): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/api/conversations/${id}/messages`, {
    method: "POST",
    body: JSON.stringify({ text }),
  });
}

export function chooseConversation(
  id: number | string,
  messageId: number,
  choice: "accept" | "skip",
  knowledgeId?: number,
): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/api/conversations/${id}/messages/${messageId}/choice`, {
    method: "POST",
    body: JSON.stringify({ choice, knowledgeId }),
  });
}

export function draftConversation(id: number | string): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/api/conversations/${id}/draft`, { method: "POST" });
}

export function commitConversation(
  id: number | string,
  payload: {
    title?: string;
    date?: string;
    summary?: string;
    content?: string;
    kind?: string;
    tags?: string[];
    problem?: string;
    cause?: string;
    solution?: string;
    lesson?: string;
    knowledgeIds?: number[];
    experienceIds?: number[];
  },
): Promise<CommitResult> {
  return request<CommitResult>(`/api/conversations/${id}/commit`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
