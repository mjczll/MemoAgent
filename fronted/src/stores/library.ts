import { defineStore } from "pinia";
import {
  createDiary as createDiaryApi,
  deleteDiary as deleteDiaryApi,
  getDiary,
  listDiaries,
  updateDiary as updateDiaryApi,
  type DiaryPage,
  type DiaryQuery,
} from "@/api/diary";
import { listDiaryKinds } from "@/api/diaryKind";
import { errorMessage } from "@/api/http";
import type { Diary, DiaryDraft, DiaryKindItem, Experience, Knowledge } from "@/types";
import { SEED_EXPERIENCES, SEED_KNOWLEDGE } from "@/mock";
import { nowStamp, todayISO, truncate, uid } from "@/lib/format";
import { searchLibrary, type LibraryData, type LibraryHit } from "@/lib/search";
import { buildGraph } from "@/lib/graph";

const LS_KEY = "memoagent:library:v2";
const LS_KEY_LEGACY = "memoagent:library:v1";

interface LibraryState {
  diaries: Diary[];
  experiences: Experience[];
  knowledge: Knowledge[];
  diaryKinds: DiaryKindItem[];
  hydrated: boolean;
  diariesLoading: boolean;
  diariesError: string | null;
}

function cloneLocalSeeds(): Pick<LibraryState, "experiences" | "knowledge"> {
  return {
    experiences: JSON.parse(JSON.stringify(SEED_EXPERIENCES)) as Experience[],
    knowledge: JSON.parse(JSON.stringify(SEED_KNOWLEDGE)) as Knowledge[],
  };
}

function readLocalAssets(): Pick<LibraryState, "experiences" | "knowledge"> {
  const raw = localStorage.getItem(LS_KEY) ?? localStorage.getItem(LS_KEY_LEGACY);
  if (!raw) return cloneLocalSeeds();
  try {
    const parsed = JSON.parse(raw) as Partial<Pick<LibraryState, "experiences" | "knowledge">>;
    if (parsed.experiences?.length || parsed.knowledge?.length) {
      return {
        experiences: parsed.experiences ?? [],
        knowledge: parsed.knowledge ?? [],
      };
    }
  } catch {
    /* 损坏的本地缓存回落到示例经验 / 知识 */
  }
  return cloneLocalSeeds();
}

function upsertDiary(list: Diary[], diary: Diary): Diary[] {
  const index = list.findIndex((item) => item.id === diary.id);
  if (index < 0) return [diary, ...list];
  const next = list.slice();
  next[index] = diary;
  return next;
}

/** 知识资产库：日记走后端，经验 / 知识阶段 1 仍本地持久化 */
export const useLibraryStore = defineStore("library", {
  state: (): LibraryState => ({
    diaries: [],
    experiences: [],
    knowledge: [],
    diaryKinds: [],
    hydrated: false,
    diariesLoading: false,
    diariesError: null,
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
    byKind: (state) => (id: string) => {
      const diary = state.diaries.find((d) => d.id === id);
      if (diary) return { kind: "diary" as const, title: diary.title };
      const experience = state.experiences.find((e) => e.id === id);
      if (experience) return { kind: "experience" as const, title: experience.title };
      const knowledge = state.knowledge.find((k) => k.id === id);
      if (knowledge) return { kind: "knowledge" as const, title: knowledge.title };
      return undefined;
    },
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
      return items.sort((a, b) => (a.at < b.at ? 1 : -1)).slice(0, 8);
    },
    pendingKnowledge(state) {
      return state.knowledge.filter((k) => k.mastery !== "已掌握").slice(0, 5);
    },
    defaultDiaryKind(state): string {
      return state.diaryKinds.find((item) => item.isDefault)?.name ?? state.diaryKinds[0]?.name ?? "技术";
    },
  },

  actions: {
    async hydrate() {
      if (this.hydrated) {
        await Promise.all([this.refreshDiaries(), this.refreshDiaryKinds()]);
        return;
      }
      Object.assign(this, readLocalAssets());
      this.hydrated = true;
      this.persist();
      await Promise.all([this.refreshDiaries(), this.refreshDiaryKinds()]);
    },

    persist() {
      try {
        localStorage.setItem(
          LS_KEY,
          JSON.stringify({
            experiences: this.experiences,
            knowledge: this.knowledge,
          }),
        );
        localStorage.removeItem(LS_KEY_LEGACY);
      } catch {
        /* 原型环境忽略写入失败 */
      }
    },

    async refreshDiaryKinds() {
      try {
        this.diaryKinds = await listDiaryKinds();
      } catch {
        this.diaryKinds = [];
      }
    },

    async refreshDiaries() {
      this.diariesLoading = true;
      try {
        const page = await listDiaries({ page: 1, size: 100, sort: "desc" });
        this.diaries = page.records;
        this.diariesError = null;
      } catch (error) {
        this.diaries = [];
        this.diariesError = errorMessage(error, "日记列表加载失败");
      } finally {
        this.diariesLoading = false;
      }
    },

    async queryDiaries(query: DiaryQuery): Promise<DiaryPage<Diary>> {
      return listDiaries(query);
    },

    async fetchDiary(id: string): Promise<Diary | undefined> {
      try {
        const diary = await getDiary(id);
        this.diaries = upsertDiary(this.diaries, diary);
        return diary;
      } catch {
        return undefined;
      }
    },

    async resetToSeed() {
      Object.assign(this, cloneLocalSeeds());
      this.persist();
      await this.refreshDiaries();
    },

    search(query: string, limit = 8): LibraryHit[] {
      return searchLibrary(query, this.data, limit);
    },

    graph(domain: string | null = null) {
      return buildGraph(this.data, domain);
    },

    async saveDraft(
      draft: DiaryDraft,
    ): Promise<{ diaryId: string; experienceId: string; newKnowledgeIds: string[] }> {
      const now = nowStamp();
      const experienceId = uid("e");
      const newKnowledgeIds: string[] = [];
      const linkedKnowledgeIds: string[] = [];

      const diaryContent = [
        draft.content,
        "---",
        "## AI 结构化提炼",
        `**问题**：${draft.extraction.problem}`,
        `**原因**：${draft.extraction.cause}`,
        `**解决方案**：${draft.extraction.solution}`,
        `**经验**：${draft.extraction.lesson}`,
      ].join("\n\n");

      const diary = await createDiaryApi({
        title: draft.title,
        content: diaryContent,
        summary: draft.summary,
        date: draft.date,
        kind: draft.kind,
        origin: "interview",
        tags: [...draft.tags],
      });

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

      diary.experienceIds = [experienceId];
      diary.knowledgeIds = linkedKnowledgeIds;
      diary.experienceCount = 1;
      diary.knowledgeCount = linkedKnowledgeIds.length;
      this.diaries = upsertDiary(this.diaries, diary);

      const experience: Experience = {
        id: experienceId,
        title: `${draft.title} —— 经验提炼`,
        problem: draft.extraction.problem,
        cause: draft.extraction.cause,
        solution: draft.extraction.solution,
        lesson: draft.extraction.lesson,
        tags: [...draft.tags],
        domain: draft.tags[0] ?? "项目开发",
        diaryId: diary.id,
        knowledgeIds: linkedKnowledgeIds,
        visibility: "private",
        createdAt: now,
      };
      this.experiences.unshift(experience);
      this.persist();
      await this.refreshDiaryKinds();
      return { diaryId: diary.id, experienceId, newKnowledgeIds };
    },

    async updateDiary(id: string, patch: Partial<Diary> & { title: string; content: string }) {
      const current = this.diaryById(id);
      const diary = await updateDiaryApi(id, {
        title: patch.title,
        content: patch.content,
        summary: patch.summary ?? current?.summary,
        date: patch.date ?? current?.date ?? todayISO(),
        kind: patch.kind ?? current?.kind ?? this.defaultDiaryKind,
        origin: patch.origin ?? current?.origin,
        tags: patch.tags ?? current?.tags,
      });
      this.diaries = upsertDiary(this.diaries, diary);
      await this.refreshDiaryKinds();
      return diary;
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

    async createDiary(input: Partial<Diary> & { title: string; content: string }): Promise<string> {
      const diary = await createDiaryApi({
        title: input.title,
        content: input.content,
        summary: input.summary,
        date: input.date ?? todayISO(),
        kind: input.kind ?? this.defaultDiaryKind,
        origin: input.origin ?? "manual",
        tags: input.tags ?? [],
      });
      this.diaries = upsertDiary(this.diaries, diary);
      await this.refreshDiaryKinds();
      return diary.id;
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

    async deleteDiary(id: string) {
      await deleteDiaryApi(id);
      this.diaries = this.diaries.filter((d) => d.id !== id);
      this.experiences = this.experiences.filter((e) => e.diaryId !== id);
      this.persist();
      await this.refreshDiaryKinds();
    },
  },
});
