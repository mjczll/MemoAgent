<template>
  <div v-if="loading" class="fade-in">
    <EmptyState icon="📔" title="正在打开日记" hint="正在从后端读取这篇记录。" />
  </div>

  <div v-else-if="diary" class="fade-in">
    <span class="crumb" @click="router.push('/diaries')">← 返回日记列表</span>

    <div class="detail-head">
      <div class="row between wrap">
        <div style="min-width: 260px">
          <h1 class="h1" style="margin-bottom: 8px">{{ diary.title }}</h1>
          <div class="row wrap small faint">
            <span>{{ diary.date }}</span>
            <span>·</span>
            <span>{{ diary.kind }}</span>
            <span>·</span>
            <span>{{ diary.origin === "interview" ? "由采访生成" : "手动记录" }}</span>
          </div>
          <div class="tags" style="margin-top: 10px">
            <TagRow :tags="diary.tags" @pick="searchTag" />
          </div>
        </div>
        <div class="row">
          <button class="btn sm" @click="router.push(`/diaries/${diary.id}/edit`)">编辑</button>
          <button class="btn sm danger" :disabled="removing" @click="confirmDelete = true">删除</button>
        </div>
      </div>
    </div>

    <div class="split">
      <div class="panel">
        <div class="panel-head">
          <b>正文</b>
          <span class="spacer" />
          <span class="small faint">{{ relative(diary.createdAt) }}记录</span>
        </div>
        <div class="panel-body">
          <MarkdownView :text="diary.content" />
        </div>
      </div>

      <div class="stack">
        <div class="panel" style="cursor: pointer" @click="router.push(`/graph/local/${diary.id}?depth=2`)">
          <div class="panel-head"><b>🔗 查看关系图</b><span class="spacer" /><span class="small faint">查看该日记的局部关系网络</span></div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>这篇日记沉淀了什么</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <div>
              <div class="small faint" style="margin-bottom: 6px">经验卡 · {{ experiences.length }}</div>
              <div
                v-for="item in experiences"
                :key="item.id"
                class="kv-item"
                style="cursor: pointer; margin-bottom: 8px"
                @click="router.push(`/experiences/${item.id}`)"
              >
                <span>经验</span>
                <p>{{ item.title }}</p>
              </div>
              <p v-if="!experiences.length" class="small faint">还没有提炼出经验卡。</p>
              <button type="button" class="btn sm" :disabled="extracting || savingExperiences" @click="extract">
                {{ extracting ? "提炼中…" : "从这篇日记提炼经验" }}
              </button>
              <p v-if="extractError" class="small" style="color: var(--danger, #e66); margin: 8px 0 0">{{ extractError }}</p>
              <p v-else-if="extracted && !candidates.length" class="small faint" style="margin: 8px 0 0">这篇没有可沉淀的经验。</p>
              <div v-for="(item, index) in candidates" :key="index" class="kv-item" style="margin-top: 8px">
                <label>
                  <input v-model="item.keep" type="checkbox" />
                  留下这条经验
                </label>
                <p><b>问题</b> {{ item.problem }}</p>
                <p v-if="item.cause"><b>原因</b> {{ item.cause }}</p>
                <p v-if="item.solution"><b>解决方案</b> {{ item.solution }}</p>
                <p v-if="item.lesson"><b>经验</b> {{ item.lesson }}</p>
                <label v-if="item.knowledge">
                  <input v-model="item.keepKnowledge" type="checkbox" :disabled="!item.keep" />
                  同时写入知识：{{ item.knowledge.title }}
                </label>
              </div>
              <button
                v-if="candidates.some((item) => item.keep)"
                type="button"
                class="btn sm primary"
                :disabled="savingExperiences"
                @click="saveExperiences"
              >
                {{ savingExperiences ? "保存中…" : "保存选中的经验" }}
              </button>
            </div>
            <div>
              <div class="small faint" style="margin-bottom: 6px">关联知识 · {{ knowledges.length }}</div>
              <div
                v-for="item in knowledges"
                :key="item.id"
                class="kv-item"
                style="cursor: pointer; margin-bottom: 8px"
                @click="router.push(`/knowledge/${item.id}`)"
              >
                <span>{{ item.domain }} · {{ item.category }}</span>
                <p>{{ item.title }}</p>
              </div>
              <p v-if="!knowledges.length" class="small faint">还没有关联知识条目。</p>
            </div>
          </div>
        </div>

        <div class="panel ai-block">
          <div class="panel-head"><b>双链</b></div>
          <div class="panel-body stack" style="gap: 8px">
            <p v-if="!wikiLinks.length" class="small faint">正文里还没有 [[双链]]。</p>
            <button
              v-for="link in wikiLinks"
              :key="link"
              class="btn ghost"
              style="justify-content: flex-start; text-align: left"
              @click="openWiki(link)"
            >
              [[{{ link }}]] · 查看相关沉淀
            </button>
          </div>
        </div>
      </div>
    </div>

    <ModalDialog v-model="confirmDelete" title="删除这篇日记？" width="460px">
      <p class="small" style="margin: 0">
        将删除《{{ diary.title }}》，同时移除由它提炼出的经验卡（关联知识条目会保留）。该操作不可撤销。
      </p>
      <template #foot>
        <div class="row" style="justify-content: flex-end; width: 100%">
          <button class="btn" @click="confirmDelete = false">取消</button>
          <button class="btn danger" :disabled="removing" @click="removeDiary">
            {{ removing ? "删除中…" : "确认删除" }}
          </button>
        </div>
      </template>
    </ModalDialog>
  </div>

  <EmptyState v-else icon="🕳" title="找不到这篇日记" hint="它可能已被删除，回到列表看看其它记录。">
    <button class="btn primary sm" @click="router.push('/diaries')">返回日记列表</button>
  </EmptyState>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { extractDiary, type ExperienceCandidate } from "@/api/diary";
import { createExperience } from "@/api/experience";
import { errorMessage } from "@/api/http";
import EmptyState from "@/components/base/EmptyState.vue";
import MarkdownView from "@/components/base/MarkdownView.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import TagRow from "@/components/base/TagRow.vue";
import { relative } from "@/lib/format";
import { extractWikiLinks } from "@/lib/markdown";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { Diary } from "@/types";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

interface OrganizedExperience extends ExperienceCandidate {
  keep: boolean;
  keepKnowledge: boolean;
}

const confirmDelete = ref(false);
const loading = ref(true);
const removing = ref(false);
const extracting = ref(false);
const extracted = ref(false);
const extractError = ref("");
const savingExperiences = ref(false);
const candidates = ref<OrganizedExperience[]>([]);
const diary = ref<Diary | undefined>();

const diaryId = computed(() => String(route.params.id ?? ""));
const experiences = computed(() => {
  const linked = new Set(diary.value?.experienceIds ?? []);
  return library.experiences.filter((item) => item.diaryId === diaryId.value || linked.has(item.id));
});
const knowledges = computed(() =>
  library.knowledge.filter((item) => item.id && diary.value?.knowledgeIds.includes(item.id)),
);
const wikiLinks = computed(() => (diary.value ? extractWikiLinks(diary.value.content) : []));

watch(
  diaryId,
  async (id) => {
    loading.value = true;
    candidates.value = [];
    extracted.value = false;
    extractError.value = "";
    await library.refreshExperiences();
    await library.refreshKnowledge();
    diary.value = id ? await library.fetchDiary(id) : undefined;
    loading.value = false;
  },
  { immediate: true },
);

async function removeDiary() {
  const target = diary.value;
  if (!target) return;
  removing.value = true;
  try {
    await library.deleteDiary(target.id);
    confirmDelete.value = false;
    ui.toast("日记已删除", "warn");
    router.push("/diaries");
  } catch (error) {
    ui.toast(errorMessage(error, "删除失败"), "error");
  } finally {
    removing.value = false;
  }
}

async function extract() {
  if (!diary.value || extracting.value) return;
  extracting.value = true;
  extractError.value = "";
  try {
    const result = await extractDiary(diary.value.id);
    candidates.value = (result.experiences ?? []).map((item) => ({
      ...item,
      keep: false,
      keepKnowledge: false,
    }));
    extracted.value = true;
    if (!candidates.value.length) {
      ui.toast("这篇没有可沉淀的经验");
    }
  } catch (error) {
    extractError.value = errorMessage(error, "提炼失败");
    ui.toast(extractError.value, "error");
  } finally {
    extracting.value = false;
  }
}

async function saveExperiences() {
  if (!diary.value || savingExperiences.value) return;
  const diaryId = diary.value.id;
  const selected = candidates.value.filter((item) => item.keep);
  if (!selected.length) return;
  savingExperiences.value = true;
  try {
    for (const item of selected) {
      const knowledge = item.keepKnowledge ? item.knowledge : null;
      await createExperience({
        diaryId,
        title: item.title,
        problem: item.problem,
        cause: item.cause,
        solution: item.solution,
        lesson: item.lesson,
        domain: item.domain,
        tags: item.tags ?? [],
        newKnowledge: knowledge
          ? [
              {
                title: knowledge.title,
                category: knowledge.category,
                domain: knowledge.domain,
                summary: knowledge.summary,
                content: knowledge.content ?? "",
                tags: knowledge.tags ?? [],
              },
            ]
          : [],
      });
    }
    await library.refreshExperiences();
    await library.refreshKnowledge();
    const refreshed = await library.fetchDiary(diaryId);
    if (refreshed) diary.value = refreshed;
    candidates.value = [];
    extracted.value = false;
    ui.toast("经验已保存");
  } catch (error) {
    ui.toast(errorMessage(error, "经验没有写上"), "error");
  } finally {
    savingExperiences.value = false;
  }
}

function searchTag(tag: string) {
  router.push({ name: "search", query: { q: tag } });
}

function openWiki(title: string) {
  const hit = library.knowledgeByTitle(title);
  if (hit) router.push(`/knowledge/${hit.id}`);
  else ui.toast(`还没有「${title}」这条知识，去知识库新建一条吧`, "info");
}
</script>
