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
  streamConversationTitle,
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
  /** 当前这条对话是否正在输出。多条对话可同时输出，所以按会话 id 管理 */
  sending: boolean;
  /** 每个会话各自的流式控制器：在 A 里等回复的同时可以去首页开 B */
  controllers: Record<number, AbortController>;
}

export const useConversationStore = defineStore("conversation", {
  state: (): ConversationState => ({
    sessions: [],
    current: null,
    sending: false,
    controllers: {},
  }),

  actions: {
    async refresh() {
      this.sessions = await listConversations();
    },

    async open(id: number | string) {
      this.current = await getConversation(id);
      this.syncSending();
    },

    /** 把 sending 对齐到「当前会话有没有在跑」——切换会话后 UI 才不会卡在停止按钮上 */
    syncSending() {
      const id = this.current?.id;
      this.sending = id != null && Boolean(this.controllers[id]);
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
      // 控制器与 sending 交给 streamInto 处理：首页此刻可能还有另一条对话在跑
      this.syncSending();
      return created.id;
    },

    stop() {
      const id = this.current?.id;
      if (id != null) {
        this.controllers[id]?.abort();
        return;
      }
      Object.values(this.controllers).forEach((controller) => controller.abort());
    },

    /**
     * 投递首条消息。不再用全局锁：不同会话可以同时投递，
     * 同一个会话的重复投递由 streamInto 里的 controllers[id] 兜住。
     */
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
      // 每个会话独立控制器：A 正在输出时去首页开 B，两边的停止按钮互不干扰
      if (this.controllers[id]) return;
      const controller = new AbortController();
      this.controllers[id] = controller;
      this.syncSending();
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
        // 标题由后端在本轮结束后异步生成，生成完通过 SSE 推过来，不再轮询
        void this.watchTitle(id);
      } catch (error) {
        if (!isAbortError(error)) throw error;
        if (live && !live.text?.trim() && this.current) {
          const index = this.current.messages.indexOf(live);
          if (index >= 0) this.current.messages.splice(index, 1);
        }
      } finally {
        delete this.controllers[id];
        this.syncSending();
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
      if (this.controllers[id]) return;
      const controller = new AbortController();
      this.controllers[id] = controller;
      this.syncSending();
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
        delete this.controllers[id];
        this.syncSending();
      }
    },

    /** 改用户消息后重发：本地先截断该条之后的内容，再流式补回新的回复 */
    async editAndResend(id: number, messageId: number, text: string) {
      if (this.controllers[id]) return;
      const controller = new AbortController();
      this.controllers[id] = controller;
      this.syncSending();
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
        delete this.controllers[id];
        this.syncSending();
      }
    },

    /**
     * 等后端推送标题。
     * 标题在回复结束后还要再跑一次模型（本地 4–12 秒），所以这里挂一条 SSE 等它，
     * 收到就更新侧栏与顶栏。超时或断线静默放弃——下次对话还会再试。
     */
    async watchTitle(id: number) {
      try {
        const title = await streamConversationTitle(id);
        if (!title) return;
        // 先就地改，界面立刻更新；再拉一次列表以服务端为准。
        // 只改本地对象不够稳：sessions 随时可能被别的 refresh 换掉，换掉后这一项又退回占位符。
        if (this.current?.id === id) this.current.title = title;
        const session = this.sessions.find((item) => item.id === id);
        if (session) session.title = title;
        await this.refresh();
      } catch {
        // 标题是锦上添花，失败不打扰用户
      }
    },
  },
});
