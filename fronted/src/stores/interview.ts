import { defineStore } from "pinia";
import type { ChatMessage, DiaryDraft } from "@/types";
import {
  FOLLOW_UP_TEMPLATES,
  INTERVIEW_CLOSING_PREFIX,
  INTERVIEW_CLOSING_SUFFIX,
  INTERVIEW_OPENING,
  INTERVIEW_STEPS,
} from "@/mock";
import { buildDraft } from "@/lib/draft";
import { echo, fillTemplate, inferTopic, type TopicGuess } from "@/lib/topic";
import { nowStamp, uid } from "@/lib/format";
import { useLibraryStore } from "./library";

const LS_KEY = "memoagent:interview:v1";
const THINK_MS = 700;

interface InterviewState {
  messages: ChatMessage[];
  answers: string[];
  draft: DiaryDraft | null;
  thinking: boolean;
  hydrated: boolean;
}

/** 采访会话：AI 按「五步法」采访用户，最后产出日记草稿 */
export const useInterviewStore = defineStore("interview", {
  state: (): InterviewState => ({
    messages: [],
    answers: [],
    draft: null,
    thinking: false,
    hydrated: false,
  }),

  getters: {
    /** 已完成的步骤数（用于左侧进度条） */
    stepIndex(state): number {
      return Math.min(state.answers.length, INTERVIEW_STEPS.length);
    },
    stepTotal(): number {
      return INTERVIEW_STEPS.length;
    },
    progressPercent(): number {
      return Math.round((this.stepIndex / INTERVIEW_STEPS.length) * 100);
    },
    finished(state): boolean {
      return Boolean(state.draft) && state.answers.length >= INTERVIEW_STEPS.length;
    },
    topicLabel(state): string {
      const text = state.answers[0] ?? state.messages.find((m) => m.role === "user")?.text ?? "";
      return inferTopic(text).label;
    },
    /** 最近一次采访的开场问题（用于侧栏"最近会话"） */
    opening(state): string {
      return state.answers[0] ?? "";
    },
  },

  actions: {
    hydrate() {
      if (this.hydrated) return;
      try {
        const raw = localStorage.getItem(LS_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as {
            messages: ChatMessage[];
            answers: string[];
            draft: DiaryDraft | null;
          };
          this.messages = parsed.messages ?? [];
          this.answers = parsed.answers ?? [];
          this.draft = parsed.draft ?? null;
        }
      } catch {
        this.messages = [];
        this.answers = [];
        this.draft = null;
      }
      if (!this.messages.length) {
        this.messages = [
          { id: uid("m"), role: "ai", text: INTERVIEW_OPENING, at: nowStamp() },
        ];
      }
      this.hydrated = true;
    },

    persist() {
      try {
        localStorage.setItem(
          LS_KEY,
          JSON.stringify({ messages: this.messages, answers: this.answers, draft: this.draft }),
        );
      } catch {
        /* 忽略 */
      }
    },

    /** 重新开始一次采访；可带入一段开场内容 */
    reset(openingText = "") {
      this.messages = [{ id: uid("m"), role: "ai", text: INTERVIEW_OPENING, at: nowStamp() }];
      this.answers = [];
      this.draft = null;
      this.thinking = false;
      this.persist();
      if (openingText.trim()) this.send(openingText.trim());
    },

    /** 用户发送一段回答，随后 Mock AI 追问下一步 */
    send(text: string) {
      const content = text.trim();
      if (!content || this.thinking) return;

      this.messages.push({ id: uid("m"), role: "user", text: content, at: nowStamp() });
      this.answers.push(content);
      this.thinking = true;
      this.persist();

      window.setTimeout(() => {
        this.reply();
      }, THINK_MS);
    },

    /** 生成一条 AI 追问 / 收尾消息 */
    reply() {
      const turn = this.answers.length;
      const last = this.answers[turn - 1] ?? "";
      const guess: TopicGuess = inferTopic(this.answers.join(" ") || last);
      let text = "";
      let action: ChatMessage["action"];

      if (turn <= FOLLOW_UP_TEMPLATES.length) {
        const template = FOLLOW_UP_TEMPLATES[turn - 1];
        if (template) {
          const react = fillTemplate(template.react, { echo: echo(last) });
          text = `${react}${template.ask}`;
        }
      } else {
        const library = useLibraryStore();
        library.hydrate();
        const draft = buildDraft(this.answers, guess, library.data);
        if (this.draft) {
          // 草稿已生成过：静默更新内容，不再重复推送「草稿已就绪」卡片
          this.draft = draft;
          text = [
            "这段我也并进草稿了 —— 上面的草稿会同步更新。",
            "",
            "确认没问题就点「保存到日记」；想继续补细节，直接说给我听就行。",
          ].join("\n");
        } else {
          this.draft = draft;
          action = "generate-diary";
          text = [
            `${INTERVIEW_CLOSING_PREFIX}这次聊的是「${guess.label}」相关的事：`,
            "",
            `> ${guess.scenario ? guess.scenario.summary : draft.summary}`,
            "",
            INTERVIEW_CLOSING_SUFFIX,
          ].join("\n");
        }
      }

      this.messages.push({ id: uid("m"), role: "ai", text, at: nowStamp(), action });
      this.thinking = false;
      this.persist();
    },

    /** 重新生成草稿（用户点了"重新整理"） */
    regenerateDraft(): boolean {
      if (this.answers.length < INTERVIEW_STEPS.length) return false;
      const library = useLibraryStore();
      library.hydrate();
      const guess = inferTopic(this.answers.join(" "));
      this.draft = buildDraft(this.answers, guess, library.data);
      this.persist();
      return true;
    },

    /** 草稿交给知识库完成落库，并回填 generated 状态 */
    markDraftSaved(diaryId: string) {
      if (this.draft) {
        this.draft.saved = true;
        this.draft.savedDiaryId = diaryId;
        this.persist();
      }
    },

    clearDraft() {
      this.draft = null;
      this.persist();
    },
  },
});
