import { defineStore } from "pinia";
import type { Diary, DiaryDraft, Experience, Knowledge } from "@/types";
import { SEED_DIARIES, SEED_EXPERIENCES, SEED_KNOWLEDGE } from "@/mock";
import { nowStamp, todayISO, truncate, uid } from "@/lib/format";
import { searchLibrary, type LibraryData, type LibraryHit } from "@/lib/search";
import { buildGraph } from "@/lib/graph";

const LS_KEY = "memoagent:library:v1";

interface LibraryState {
  diaries: Diary[];
  experiences: Experience[];
  knowledge: Knowledge[];
  hydrated: boolean;
}

function cloneSeeds(): Pick<LibraryState, "diaries" | "experiences" | "knowledge"> {
  return {
    diaries: JSON.parse(JSON.stringify(SEED_DIARIES)) as Diary[],
    experiences: JSON.parse(JSON.stringify(SEED_EXPERIENCES)) as Experience[],
    knowledge: JSON.parse(JSON.stringify(SEED_KNOWLEDGE)) as Knowledge[],
  };
}

/** 知识资产库：日记 / 经验 / 知识 的唯一数据源（本地持久化，Mock 数据） */
export const useLibraryStore = defineStore("library", {
  state: (): LibraryState => ({
    diaries: [],
    experiences: [],
    knowledge: [],
    hydrated: false,
  }),

  getters: {
    data(state): LibraryData {
      return { diaries: state.diaries, experiences: state.experiences, knowledge: state.knowledge };
    },
    diaryById: (state) => (id: string) => state.diaries.find((d) => d.id === id),
    experienceById: (state) => (id: string) => state.experiences.find((e) => e.id === id),
    knowledgeById: (state) => (id: string) => state.knowledge.find((k) => k.id === id),
    knowledgeByTitle: (state) => (title: string) =>
      state.knowledge.find((k) => k.title === title.trim()),
    /** 按 id 反查实体（跨日记 / 经验 / 知识） */
    byKind: (state) => (id: string) => {
      const diary = state.diaries.find((d) => d.id === id);
      if (diary) return { kind: "diary" as const, title: diary.title };
      const experience = state.experiences.find((e) => e.id === id);
      if (experience) return { kind: "experience" as const, title: experience.title };
      const knowledge = state.knowledge.find((k) => k.id === id);
      if (knowledge) return { kind: "knowledge" as const, title: knowledge.title };
      return undefined;
    },
    /** 首页 / 侧栏统计 */
    stats(state) {
      const mastered = state.knowledge.filter((k) => k.mastery === "已掌握").length;
      const domainCount = new Set([
        ...state.knowledge.map((k) => k.domain),
        ...state.experiences.map((e) => e.domain),
      ]).size;
      const interviewOrigin = state.diaries.filter((d) => d.origin === "interview").length;
      return {
        diaries: state.diaries.length,
        experiences: state.experiences.length,
        knowledge: state.knowledge.length,
        mastered,
        masteryRate: state.knowledge.length
          ? Math.round((mastered / state.knowledge.length) * 100)
          : 0,
        domains: domainCount,
        interviewOrigin,
        linkCount: state.knowledge.reduce((sum, k) => sum + k.relatedIds.length, 0),
      };
    },
    /** 各领域条目数（侧栏"我的领域"） */
    domainCounts(state) {
      const map: Record<string, number> = {};
      state.knowledge.forEach((k) => {
        map[k.domain] = (map[k.domain] ?? 0) + 1;
      });
      state.experiences.forEach((e) => {
        map[e.domain] = (map[e.domain] ?? 0) + 1;
      });
      return map;
    },
    /** 最近活动（首页时间轴） */
    recentActivity(state) {
      const items = [
        ...state.diaries.map((d) => ({
          id: d.id,
          kind: "diary" as const,
          title: d.title,
          at: d.createdAt,
          date: d.date,
        })),
        ...state.experiences.map((e) => ({
          id: e.id,
          kind: "experience" as const,
          title: e.title,
          at: e.createdAt,
          date: e.createdAt.slice(0, 10),
        })),
        ...state.knowledge.map((k) => ({
          id: k.id,
          kind: "knowledge" as const,
          title: k.title,
          at: k.updatedAt,
          date: k.updatedAt.slice(0, 10),
        })),
      ];
      return items
        .sort((a, b) => (a.at < b.at ? 1 : -1))
        .slice(0, 8);
    },
    /** 待补充的知识（首页"待办"） */
    pendingKnowledge(state) {
      return state.knowledge.filter((k) => k.mastery !== "已掌握").slice(0, 5);
    },
  },

  actions: {
    hydrate() {
      if (this.hydrated) return;
      try {
        const raw = localStorage.getItem(LS_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as Pick<LibraryState, "diaries" | "experiences" | "knowledge">;
          this.diaries = parsed.diaries ?? [];
          this.experiences = parsed.experiences ?? [];
          this.knowledge = parsed.knowledge ?? [];
        } else {
          Object.assign(this, cloneSeeds());
        }
      } catch {
        Object.assign(this, cloneSeeds());
      }
      if (!this.diaries.length && !this.knowledge.length) Object.assign(this, cloneSeeds());
      this.hydrated = true;
      this.persist();
    },

    persist() {
      try {
        localStorage.setItem(
          LS_KEY,
          JSON.stringify({
            diaries: this.diaries,
            experiences: this.experiences,
            knowledge: this.knowledge,
          }),
        );
      } catch {
        /* 原型环境忽略写入失败 */
      }
    },

    resetToSeed() {
      Object.assign(this, cloneSeeds());
      this.persist();
    },

    search(query: string, limit = 8): LibraryHit[] {
      return searchLibrary(query, this.data, limit);
    },

    graph(domain: string | null = null) {
      return buildGraph(this.data, domain);
    },

    /** 保存采访草稿：生成日记 + 经验，并回写/新建知识条目 */
    saveDraft(draft: DiaryDraft): { diaryId: string; experienceId: string; newKnowledgeIds: string[] } {
      const now = nowStamp();
      const experienceId = uid("e");
      const newKnowledgeIds: string[] = [];
      const linkedKnowledgeIds: string[] = [];

      draft.suggestedKnowledge.forEach((suggestion) => {
        if (suggestion.existingId) {
          const existing = this.knowledge.find((k) => k.id === suggestion.existingId);
          if (existing) {
            if (!existing.sourceExperienceIds.includes(experienceId)) {
              existing.sourceExperienceIds.push(experienceId);
            }
            existing.updatedAt = now;
            linkedKnowledgeIds.push(existing.id);
            return;
          }
        }
        const knowledgeId = uid("k");
        newKnowledgeIds.push(knowledgeId);
        linkedKnowledgeIds.push(knowledgeId);
        const newKnowledge: Knowledge = {
          id: knowledgeId,
          title: suggestion.title,
          category: draft.tags[0] ?? "未分类",
          domain: draft.tags[0] ?? "项目开发",
          tags: [...draft.tags],
          summary: truncate(draft.extraction.lesson, 60),
          content: [
            `## 问题\n\n${draft.extraction.problem}`,
            `## 原因\n\n${draft.extraction.cause}`,
            `## 解决方案\n\n${draft.extraction.solution}`,
            `## 经验\n\n${draft.extraction.lesson}`,
            `---\n\n*由日记《${draft.title}》于 ${todayISO()} 沉淀，待补充完善。*`,
          ].join("\n\n"),
          relatedIds: draft.suggestedKnowledge
            .map((s) => s.existingId)
            .filter((id): id is string => Boolean(id)),
          sourceExperienceIds: [experienceId],
          mastery: "待补充",
          visibility: "private",
          updatedAt: now,
        };
        this.knowledge.push(newKnowledge);
      });

      const diaryId = uid("d");
      const diaryContent = [
        draft.content,
        "---",
        "## AI 结构化提炼",
        `**问题**：${draft.extraction.problem}`,
        `**原因**：${draft.extraction.cause}`,
        `**解决方案**：${draft.extraction.solution}`,
        `**经验**：${draft.extraction.lesson}`,
      ].join("\n\n");

      const diary: Diary = {
        id: diaryId,
        title: draft.title,
        date: draft.date,
        kind: draft.kind,
        tags: [...draft.tags],
        summary: draft.summary,
        content: diaryContent,
        visibility: "private",
        experienceIds: [experienceId],
        knowledgeIds: linkedKnowledgeIds,
        origin: "interview",
        createdAt: now,
      };

      const experience: Experience = {
        id: experienceId,
        title: `${draft.title} —— 经验提炼`,
        problem: draft.extraction.problem,
        cause: draft.extraction.cause,
        solution: draft.extraction.solution,
        lesson: draft.extraction.lesson,
        tags: [...draft.tags],
        domain: draft.tags[0] ?? "项目开发",
        diaryId,
        knowledgeIds: linkedKnowledgeIds,
        visibility: "private",
        createdAt: now,
      };

      this.diaries.unshift(diary);
      this.experiences.unshift(experience);

      this.persist();
      return { diaryId, experienceId, newKnowledgeIds };
    },

    updateDiary(id: string, patch: Partial<Diary>) {
      const target = this.diaries.find((d) => d.id === id);
      if (!target) return;
      Object.assign(target, patch);
      this.persist();
    },

    updateKnowledge(id: string, patch: Partial<Knowledge>) {
      const target = this.knowledge.find((k) => k.id === id);
      if (!target) return;
      Object.assign(target, patch, { updatedAt: nowStamp() });
      this.persist();
    },

    updateExperience(id: string, patch: Partial<Experience>) {
      const target = this.experiences.find((e) => e.id === id);
      if (!target) return;
      Object.assign(target, patch);
      this.persist();
    },

    /** 手动新建一篇日记 */
    createDiary(input: Partial<Diary> & { title: string; content: string }): string {
      const id = uid("d");
      const now = nowStamp();
      this.diaries.unshift({
        id,
        title: input.title,
        date: input.date ?? todayISO(),
        kind: input.kind ?? "技术",
        tags: input.tags ?? [],
        summary: input.summary ?? truncate(input.content, 60),
        content: input.content,
        visibility: input.visibility ?? "private",
        experienceIds: input.experienceIds ?? [],
        knowledgeIds: input.knowledgeIds ?? [],
        origin: input.origin ?? "manual",
        createdAt: now,
      });
      this.persist();
      return id;
    },

    createKnowledge(input: Partial<Knowledge> & { title: string; content: string }): string {
      const id = uid("k");
      const now = nowStamp();
      this.knowledge.unshift({
        id,
        title: input.title,
        category: input.category ?? "未分类",
        domain: input.domain ?? "项目开发",
        tags: input.tags ?? [],
        summary: input.summary ?? truncate(input.content, 60),
        content: input.content,
        relatedIds: input.relatedIds ?? [],
        sourceExperienceIds: input.sourceExperienceIds ?? [],
        mastery: input.mastery ?? "待补充",
        visibility: input.visibility ?? "private",
        updatedAt: now,
      });
      this.persist();
      return id;
    },

    deleteDiary(id: string) {
      const diary = this.diaries.find((d) => d.id === id);
      this.diaries = this.diaries.filter((d) => d.id !== id);
      if (diary) {
        this.experiences = this.experiences.filter((e) => e.diaryId !== id);
      }
      this.persist();
    },
  },
});
