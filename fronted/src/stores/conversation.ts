import { defineStore } from "pinia";
import {
  chooseConversation,
  commitConversation,
  createConversation,
  deleteConversation,
  draftConversation,
  editConversationMessage,
  getConversation,
  listConversations,
  isAbortError,
  pinConversation,
  regenerateConversation,
  renameConversation,
  streamConversationMessage,
  type CommitResult,
  type ConversationDetail,
  type ConversationListItem,
  type ConversationMessage,
} from "@/api/conversation";

/** 后端还没生成出标题时的占位，与 ConversationTitleService.PLACEHOLDER 保持一致 */
const NEW_TITLE = "新对话";

interface ConversationState {
  sessions: ConversationListItem[];
  current: ConversationDetail | null;
  sending: boolean;
  abortController: AbortController | null;
  /** 首页首条消息的投递锁：openFromHome 和 deliver 之间有一段 await，靠它挡住并发重复投递 */
  delivering: boolean;
}

export const useConversationStore = defineStore("conversation", {
  state: (): ConversationState => ({
    sessions: [],
    current: null,
    sending: false,
    abortController: null,
    delivering: false,
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
      // 标题由后端在首轮结束后异步生成，这里先显示占位，不要拿首句顶上
      const title = created.title || NEW_TITLE;
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
      if (this.delivering) {
        // 已经有一次投递在跑，这次丢弃，并把抢锁时置上的 sending 复位
        this.sending = false;
        return;
      }
      this.delivering = true;
      try {
        await this.streamInto(id, text, false);
      } finally {
        this.delivering = false;
      }
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
        // 标题是后台生成的：回复先出来，过一两秒标题才会变成内容主体
        if (detail.title === NEW_TITLE) {
          void this.settleTitle(id);
        }
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

    async rename(id: number, title: string) {
      await renameConversation(id, title);
      await this.refresh();
      if (this.current?.id === id) this.current.title = title;
    },

    async togglePin(id: number, pinned: boolean) {
      await pinConversation(id, pinned);
      await this.refresh();
    },

    /** 重新生成某条助手回复：先清空气泡再往里流，视觉上就是「它重新想一遍」 */
    async regenerate(id: number, messageId: number) {
      if (this.sending) return;
      const controller = new AbortController();
      this.abortController = controller;
      this.sending = true;
      const live = this.current?.messages.find((item) => item.id === messageId) ?? null;
      if (live) live.text = "";
      try {
        const detail = await regenerateConversation(
          id,
          (chunk) => {
            if (live) live.text += chunk;
          },
          controller.signal,
        );
        if (this.current?.id === detail.id) this.current = detail;
        await this.refresh();
      } catch (error) {
        if (!isAbortError(error)) throw error;
      } finally {
        if (this.abortController === controller) this.abortController = null;
        this.sending = false;
      }
    },

    /** 改用户消息后重发：本地先截断该条之后的内容，再流式补回新的回复 */
    async editAndResend(id: number, messageId: number, text: string) {
      if (this.sending) return;
      const controller = new AbortController();
      this.abortController = controller;
      this.sending = true;
      const now = new Date().toISOString();
      let live: ConversationMessage | null = null;
      if (this.current?.id === id) {
        const index = this.current.messages.findIndex((item) => item.id === messageId);
        if (index >= 0) {
          this.current.messages[index].text = text;
          this.current.messages.splice(index + 1);
          this.current.messages.push({
            id: -2,
            role: "assistant",
            kind: "interview",
            text: "",
            createdAt: now,
          });
          live = this.current.messages[this.current.messages.length - 1];
        }
      }
      try {
        const detail = await editConversationMessage(
          id,
          messageId,
          text,
          (chunk) => {
            if (live) live.text += chunk;
          },
          controller.signal,
        );
        if (this.current?.id === detail.id) this.current = detail;
        await this.refresh();
      } catch (error) {
        if (!isAbortError(error)) throw error;
      } finally {
        if (this.abortController === controller) this.abortController = null;
        this.sending = false;
      }
    },

    /**
     * 后台生成标题需要一两秒，这里轻量轮询几次（本地模型偶尔要 4–5 秒）。
     * 只在标题仍是「新对话」时继续，一旦变了就停下；失败静默，不打扰用户。
     */
    async settleTitle(id: number) {
      for (let attempt = 0; attempt < 8; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, 1500));
        try {
          await this.refresh();
        } catch {
          return;
        }
        const session = this.sessions.find((item) => item.id === id);
        if (!session) return;
        if (session.title === NEW_TITLE) continue;
        if (this.current?.id === id) this.current.title = session.title;
        return;
      }
    },
  },
});
