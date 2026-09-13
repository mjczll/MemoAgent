import { defineStore } from "pinia";
import type { AgentMessage, AgentThread } from "@/types";
import { AGENT_SUGGESTIONS } from "@/mock";
import { answerQuestion } from "@/lib/agent";
import { nowStamp, uid } from "@/lib/format";
import { useLibraryStore } from "./library";

const LS_KEY = "memoagent:agent:v1";
const THINK_MS = 900;

interface AgentState {
  threads: AgentThread[];
  messagesMap: Record<string, AgentMessage[]>;
  activeThreadId: string;
  thinking: boolean;
  hydrated: boolean;
}

function welcomeMessage(): AgentMessage {
  return {
    id: uid("am"),
    role: "ai",
    text: "我们在聊你过去的事。你可以问我「我之前怎么解决 Redis 缓存击穿的」，我会翻你的日记、经验与知识条目来回答。",
    at: nowStamp(),
  };
}

/** Agent 问答：全程 Mock —— 预设问答 + 本地知识库检索兜底 */
export const useAgentStore = defineStore("agent", {
  state: (): AgentState => ({
    threads: [],
    messagesMap: {},
    activeThreadId: "thread-1",
    thinking: false,
    hydrated: false,
  }),

  getters: {
    activeMessages(state): AgentMessage[] {
      return state.messagesMap[state.activeThreadId] ?? [];
    },
    activeThread(state): AgentThread | undefined {
      return state.threads.find((t) => t.id === state.activeThreadId);
    },
    suggestions(): string[] {
      return [...AGENT_SUGGESTIONS];
    },
    /** 当前会话中最近一次用户提问（用于侧栏"最近会话"） */
    lastQuestion(state): string {
      const list = state.messagesMap[state.activeThreadId] ?? [];
      for (let i = list.length - 1; i >= 0; i -= 1) {
        if (list[i]?.role === "user") return list[i]!.text;
      }
      return "";
    },
  },

  actions: {
    hydrate() {
      if (this.hydrated) return;
      try {
        const raw = localStorage.getItem(LS_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as {
            threads: AgentThread[];
            messagesMap: Record<string, AgentMessage[]>;
            activeThreadId: string;
          };
          this.threads = parsed.threads ?? [];
          this.messagesMap = parsed.messagesMap ?? {};
          this.activeThreadId = parsed.activeThreadId ?? "thread-1";
        }
      } catch {
        this.threads = [];
        this.messagesMap = {};
      }
      if (!this.threads.length) {
        this.threads = [
          { id: "thread-1", title: "怎么用我的知识库？", updatedAt: nowStamp() },
        ];
        this.messagesMap = { "thread-1": [welcomeMessage()] };
        this.activeThreadId = "thread-1";
      }
      if (!this.messagesMap[this.activeThreadId]) {
        this.messagesMap[this.activeThreadId] = [welcomeMessage()];
      }
      this.hydrated = true;
    },

    persist() {
      try {
        localStorage.setItem(
          LS_KEY,
          JSON.stringify({
            threads: this.threads,
            messagesMap: this.messagesMap,
            activeThreadId: this.activeThreadId,
          }),
        );
      } catch {
        /* 忽略 */
      }
    },

    pushMessage(message: AgentMessage) {
      const list = this.messagesMap[this.activeThreadId] ?? [];
      this.messagesMap[this.activeThreadId] = [...list, message];
    },

    /** 提问：包含"检索轨迹"与"引用来源"的 Mock 回答 */
    ask(question: string) {
      const text = question.trim();
      if (!text || this.thinking) return;

      this.pushMessage({ id: uid("am"), role: "user", text, at: nowStamp() });
      const thread = this.threads.find((t) => t.id === this.activeThreadId);
      if (thread) {
        thread.updatedAt = nowStamp();
        if (thread.title === "怎么用我的知识库？") thread.title = text.slice(0, 18);
      }
      this.thinking = true;
      this.persist();

      window.setTimeout(() => {
        const library = useLibraryStore();
        library.hydrate();
        const answer = answerQuestion(text, library.data);
        this.pushMessage({
          id: uid("am"),
          role: "ai",
          text: answer.text,
          at: nowStamp(),
          trace: answer.trace,
          refs: answer.refs,
        });
        this.thinking = false;
        this.persist();
      }, THINK_MS);
    },

    newThread() {
      const id = uid("thread");
      this.threads.unshift({ id, title: "新会话", updatedAt: nowStamp() });
      this.messagesMap[id] = [welcomeMessage()];
      this.activeThreadId = id;
      this.persist();
    },

    switchThread(id: string) {
      if (!this.threads.some((t) => t.id === id)) return;
      this.activeThreadId = id;
      if (!this.messagesMap[id]) this.messagesMap[id] = [welcomeMessage()];
      this.persist();
    },

    renameActive(title: string) {
      const t = this.threads.find((t) => t.id === this.activeThreadId);
      if (t) {
        t.title = title.slice(0, 24) || "新会话";
        this.persist();
      }
    },

    deleteThread(id: string) {
      this.threads = this.threads.filter((t) => t.id !== id);
      delete this.messagesMap[id];
      if (!this.threads.length) {
        this.threads = [{ id: "thread-1", title: "怎么用我的知识库？", updatedAt: nowStamp() }];
      }
      if (!this.threads.some((t) => t.id === this.activeThreadId)) {
        this.activeThreadId = this.threads[0]!.id;
      }
      if (!this.messagesMap[this.activeThreadId]) {
        this.messagesMap[this.activeThreadId] = [welcomeMessage()];
      }
      this.persist();
    },
  },
});
