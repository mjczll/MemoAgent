import { defineStore } from "pinia";
import {
  chooseConversation,
  commitConversation,
  createConversation,
  deleteConversation,
  draftConversation,
  getConversation,
  listConversations,
  isAbortError,
  streamConversationMessage,
  type CommitResult,
  type ConversationDetail,
  type ConversationListItem,
} from "@/api/conversation";

interface ConversationState {
  sessions: ConversationListItem[];
  current: ConversationDetail | null;
  sending: boolean;
  abortController: AbortController | null;
}

export const useConversationStore = defineStore("conversation", {
  state: (): ConversationState => ({
    sessions: [],
    current: null,
    sending: false,
    abortController: null,
  }),

  actions: {
    async refresh() {
      this.sessions = await listConversations();
    },

    async open(id: number | string) {
      this.current = await getConversation(id);
    },

    async openFromHome(text: string): Promise<number> {
      const content = text.trim();
      const created = await createConversation();
      const title = content.slice(0, 24) || created.title;
      const now = new Date().toISOString();
      this.current = {
        ...created,
        title,
        messages: [
          {
            id: -1,
            role: "user",
            kind: "interview",
            text: content,
            createdAt: now,
          },
        ],
      };
      this.sessions = [
        { id: created.id, title, status: "active", updatedAt: now },
        ...this.sessions.filter((item) => item.id !== created.id),
      ];
      this.abortController = new AbortController();
      this.sending = true;
      return created.id;
    },

    stop() {
      this.abortController?.abort();
    },

    async deliver(id: number, text: string) {
      await this.streamInto(id, text, false);
    },

    async send(text: string) {
      const content = text.trim();
      if (!content || this.sending) return;
      if (!this.current || this.current.status === "committed") {
        const id = await this.openFromHome(content);
        await this.deliver(id, content);
        return;
      }
      await this.streamInto(this.current.id, content, true);
    },

    async streamInto(id: number, text: string, showUser: boolean) {
      const controller = this.abortController ?? new AbortController();
      this.abortController = controller;
      if (controller.signal.aborted) {
        this.sending = false;
        this.abortController = null;
        return;
      }
      this.sending = true;
      const now = new Date().toISOString();
      if (this.current?.id === id) {
        if (showUser) {
          this.current.messages.push({
            id: -1,
            role: "user",
            kind: "interview",
            text,
            createdAt: now,
          });
        }
        this.current.messages.push({
          id: -2,
          role: "assistant",
          kind: "interview",
          text: "",
          createdAt: now,
        });
      }
      const live = this.current?.id === id ? this.current.messages[this.current.messages.length - 1] : null;
      try {
        const detail = await streamConversationMessage(id, text, (chunk) => {
          if (live) live.text += chunk;
        }, controller.signal);
        if (this.current?.id === detail.id) this.current = detail;
        await this.refresh();
      } catch (error) {
        if (!isAbortError(error)) throw error;
        if (live && !live.text?.trim() && this.current) {
          const index = this.current.messages.indexOf(live);
          if (index >= 0) this.current.messages.splice(index, 1);
        }
      } finally {
        if (this.abortController === controller) this.abortController = null;
        this.sending = false;
      }
    },

    async choose(messageId: number, choice: "accept" | "skip", knowledgeId?: number) {
      if (!this.current) return;
      this.current = await chooseConversation(this.current.id, messageId, choice, knowledgeId);
    },

    async draft() {
      if (!this.current) return null;
      this.current = await draftConversation(this.current.id);
      await this.refresh();
      return this.current;
    },

    async commit(payload: {
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
    }): Promise<CommitResult> {
      if (!this.current) {
        throw new Error("没有正在进行的对话");
      }
      const result = await commitConversation(this.current.id, payload);
      this.current = await getConversation(this.current.id);
      await this.refresh();
      return result;
    },

    async remove(id: number) {
      await deleteConversation(id);
      if (this.current?.id === id) this.current = null;
      await this.refresh();
    },
  },
});
