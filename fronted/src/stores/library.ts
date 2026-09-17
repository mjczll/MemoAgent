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
import {
  createExperience as createExperienceApi,
  getExperience,
  listExperiences,
  updateExperience as updateExperienceApi,
} from "@/api/experience";
import {
  createKnowledge as createKnowledgeApi,
  deleteKnowledge as deleteKnowledgeApi,
  getKnowledge,
  listKnowledge,
  updateKnowledge as updateKnowledgeApi,
} from "@/api/knowledge";
import { errorMessage } from "@/api/http";
import type { Diary, DiaryDraft, DiaryKindItem, Experience, Knowledge } from "@/types";
import { todayISO, truncate } from "@/lib/format";
import { searchLibrary, type LibraryData, type LibraryHit } from "@/lib/search";
import { buildGraph } from "@/lib/graph";

interface LibraryState {
  diaries: Diary[];
  experiences: Experience[];
  knowledge: Knowledge[];
  diaryKinds: DiaryKindItem[];
  hydrated: boolean;
  diariesLoading: boolean;
  diariesError: string | null;
}

function upsertById<T extends { id: string }>(list: T[], item: T): T[] {
  const index = list.findIndex((row) => row.id === item.id);
  if (index < 0) return [item, ...list];
  const next = list.slice();
  next[index] = item;
  return next;
}

/** 知识资产库：日记 / 经验 / 知识均走后端 */
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
      return state.diaryKinds.find((item) => item.isDefault)?.name ?? state.diaryKinds[0]?.name ?? "";
    },
  },

  actions: {
    async hydrate() {
      this.hydrated = true;
      await Promise.all([
        this.refreshDiaries(),
        this.refreshDiaryKinds(),
        this.refreshExperiences(),
        this.refreshKnowledge(),
      ]);
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

    async refreshExperiences() {
      try {
        const page = await listExperiences({ page: 1, size: 100 });
        this.experiences = page.records;
      } catch {
        this.experiences = [];
      }
    },

    async refreshKnowledge() {
      try {
        const page = await listKnowledge({ page: 1, size: 100 });
        this.knowledge = page.records;
      } catch {
        this.knowledge = [];
      }
    },

    async queryDiaries(query: DiaryQuery): Promise<DiaryPage<Diary>> {
      return listDiaries(query);
    },

    async fetchDiary(id: string): Promise<Diary | undefined> {
      try {
        const diary = await getDiary(id);
        this.diaries = upsertById(this.diaries, diary);
        return diary;
      } catch {
        return undefined;
      }
    },

    async fetchExperience(id: string): Promise<Experience | undefined> {
      try {
        const experience = await getExperience(id);
        this.experiences = upsertById(this.experiences, experience);
        return experience;
      } catch {
        return undefined;
      }
    },

    async fetchKnowledgeItem(id: string): Promise<Knowledge | undefined> {
      try {
        const knowledge = await getKnowledge(id);
        this.knowledge = upsertById(this.knowledge, knowledge);
        return knowledge;
      } catch {
        return undefined;
      }
    },

    async resetToSeed() {
      await this.hydrate();
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

      const existingIds: string[] = [];
      const newKnowledge = draft.suggestedKnowledge.flatMap((suggestion) => {
        if (suggestion.existingId && this.knowledge.some((item) => item.id === suggestion.existingId)) {
          existingIds.push(suggestion.existingId);
          return [];
        }
        return [
          {
            title: suggestion.title,
            category: draft.tags[0] ?? "未分类",
            domain: draft.tags[0] ?? "项目开发",
            summary: truncate(draft.extraction.lesson, 60),
            content: [
              `## 问题\n\n${draft.extraction.problem}`,
              `## 原因\n\n${draft.extraction.cause}`,
              `## 解决方案\n\n${draft.extraction.solution}`,
              `## 经验\n\n${draft.extraction.lesson}`,
              `---\n\n由日记《${draft.title}》于 ${todayISO()} 沉淀，待补充完善。`,
            ].join("\n\n"),
            tags: [...draft.tags],
            relatedIds: draft.suggestedKnowledge
              .map((item) => item.existingId)
              .filter((id): id is string => Boolean(id)),
          },
        ];
      });

      const experience = await createExperienceApi({
        diaryId: diary.id,
        title: `${draft.title} —— 经验提炼`,
        problem: draft.extraction.problem,
        cause: draft.extraction.cause,
        solution: draft.extraction.solution,
        lesson: draft.extraction.lesson,
        domain: draft.tags[0] ?? "项目开发",
        tags: [...draft.tags],
        knowledgeIds: existingIds,
        newKnowledge,
      });

      this.diaries = upsertById(this.diaries, diary);
      this.experiences = upsertById(this.experiences, experience);
      await Promise.all([this.refreshDiaryKinds(), this.refreshKnowledge(), this.refreshDiaries()]);
      return {
        diaryId: diary.id,
        experienceId: experience.id,
        newKnowledgeIds: experience.knowledgeIds.filter((id) => !existingIds.includes(id)),
      };
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
      this.diaries = upsertById(this.diaries, diary);
      await this.refreshDiaryKinds();
      return diary;
    },

    async updateKnowledge(id: string, patch: Partial<Knowledge>) {
      const current = this.knowledgeById(id);
      if (!current) return;
      const knowledge = await updateKnowledgeApi(id, {
        title: patch.title ?? current.title,
        content: patch.content ?? current.content,
        summary: patch.summary ?? current.summary,
        category: patch.category ?? current.category,
        domain: patch.domain ?? current.domain,
        mastery: patch.mastery ?? current.mastery,
        tags: patch.tags ?? current.tags,
        relatedIds: patch.relatedIds ?? current.relatedIds,
        sourceExperienceIds: patch.sourceExperienceIds ?? current.sourceExperienceIds,
      });
      this.knowledge = upsertById(this.knowledge, knowledge);
      return knowledge;
    },

    async updateExperience(id: string, patch: Partial<Experience>) {
      const current = this.experienceById(id);
      if (!current) return;
      const experience = await updateExperienceApi(id, {
        title: patch.title ?? current.title,
        problem: patch.problem ?? current.problem,
        cause: patch.cause ?? current.cause,
        solution: patch.solution ?? current.solution,
        lesson: patch.lesson ?? current.lesson,
        domain: patch.domain ?? current.domain,
        tags: patch.tags ?? current.tags,
        knowledgeIds: patch.knowledgeIds ?? current.knowledgeIds,
      });
      this.experiences = upsertById(this.experiences, experience);
      return experience;
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
      this.diaries = upsertById(this.diaries, diary);
      await this.refreshDiaryKinds();
      return diary.id;
    },

    async createKnowledge(input: Partial<Knowledge> & { title: string; content: string }): Promise<string> {
      const knowledge = await createKnowledgeApi({
        title: input.title,
        content: input.content,
        category: input.category ?? "未分类",
        domain: input.domain ?? "项目开发",
        tags: input.tags ?? [],
        summary: input.summary ?? truncate(input.content, 60),
        relatedIds: input.relatedIds ?? [],
        sourceExperienceIds: input.sourceExperienceIds ?? [],
        mastery: input.mastery ?? "待补充",
      });
      this.knowledge = upsertById(this.knowledge, knowledge);
      return knowledge.id;
    },

    async deleteDiary(id: string) {
      await deleteDiaryApi(id);
      this.diaries = this.diaries.filter((d) => d.id !== id);
      this.experiences = this.experiences.filter((e) => e.diaryId !== id);
      await Promise.all([this.refreshDiaryKinds(), this.refreshExperiences()]);
    },

    async deleteKnowledge(id: string) {
      await deleteKnowledgeApi(id);
      this.knowledge = this.knowledge
        .filter((item) => item.id !== id)
        .map((item) => ({
          ...item,
          relatedIds: item.relatedIds.filter((relatedId) => relatedId !== id),
        }));
      this.experiences = this.experiences.map((item) => ({
        ...item,
        knowledgeIds: item.knowledgeIds.filter((knowledgeId) => knowledgeId !== id),
      }));
      this.diaries = this.diaries.map((item) => {
        const knowledgeIds = (item.knowledgeIds ?? []).filter((knowledgeId) => knowledgeId !== id);
        return {
          ...item,
          knowledgeIds,
          knowledgeCount: knowledgeIds.length,
        };
      });
    },
  },
});
