<template>
  <div class="fade-in">
    <span class="crumb" @click="router.back()">← 返回</span>
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">{{ isEdit ? "编辑日记" : "写一篇日记" }}</h1>
        <p class="lede" style="margin-bottom: 0">
          手动记录适合补充采访之外的日常；保存后会出现在日记列表，并可继续沉淀为知识。
        </p>
      </div>
      <button type="button" class="btn" @click="router.back()">取消</button>
      <button type="button" class="btn primary" :disabled="!canSave" @click="save">
        {{ saving ? "保存中…" : "保存" }}
      </button>
    </div>
    <p v-if="saveError" class="small" style="color: var(--danger, #e66); margin: -8px 0 12px">{{ saveError }}</p>

    <div class="split">
      <div class="panel">
        <div class="panel-head"><b>正文</b><span class="spacer" /><span class="small faint">支持 Markdown 与 [[双链]]</span></div>
        <div class="panel-body stack">
          <div class="field">
            <label>标题</label>
            <input v-model="form.title" class="input" placeholder="例如：一次 Nginx 502 的排查记录" />
          </div>
          <div class="field">
            <label>正文</label>
            <textarea
              v-model="form.content"
              class="textarea"
              style="min-height: 320px"
              placeholder="## 背景&#10;&#10;发生了什么…&#10;&#10;## 解决&#10;&#10;…"
            />
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>元信息</b></div>
          <div class="panel-body stack">
            <div class="field">
              <label>日期</label>
              <input v-model="form.date" type="date" class="input" />
            </div>
            <div class="field">
              <label>类型</label>
              <SuggestCombo
                v-model="form.kind"
                :options="kindOptions"
                :maxlength="32"
                placeholder="选择已有类型，或输入新类型"
                aria-label="选择已有类型"
              />
            </div>
            <div class="field">
              <label>标签（逗号分隔）</label>
              <input v-model="form.tagsText" class="input" placeholder="Redis, 缓存, 排查" />
            </div>
            <div class="field">
              <label>一句话摘要</label>
              <textarea v-model="form.summary" class="textarea" style="min-height: 72px" />
            </div>
            <div class="row small faint">
              <span>{{ form.content.length }} 字</span>
              <span>·</span>
              <span>预计 {{ Math.max(1, Math.round(form.content.length / 220)) }} 分钟读完</span>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>AI 整理</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <p class="small faint" style="margin: 0">
              根据当前正文整理标题、正文、类型、摘要和标签，并列出可以留下的经验。不会直接保存。
            </p>
            <button type="button" class="btn" :disabled="!canOrganize" @click="organize">
              {{ organizing ? "整理中…" : "AI 整理" }}
            </button>
            <p v-if="organizeError" class="small" style="color: var(--danger, #e66); margin: 0">{{ organizeError }}</p>
            <p v-else-if="organized && !candidates.length" class="small faint" style="margin: 0">这篇没有可沉淀的经验。</p>
            <div v-for="(item, index) in candidates" :key="index" class="kv-item">
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
          </div>
        </div>

        <div class="panel ai-block">
          <div class="panel-head"><b>预览</b></div>
          <div class="panel-body">
            <MarkdownView :text="form.content || '*还没有内容*'" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { generateDiary, type ExperienceCandidate } from "@/api/diary";
import { createExperience } from "@/api/experience";
import MarkdownView from "@/components/base/MarkdownView.vue";
import SuggestCombo from "@/components/base/SuggestCombo.vue";
import { normalizeKind } from "@/lib/kind";
import { todayISO } from "@/lib/format";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { DiaryKind } from "@/types";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

interface OrganizedExperience extends ExperienceCandidate {
  keep: boolean;
  keepKnowledge: boolean;
}

const saving = ref(false);
const saveError = ref("");
const organizing = ref(false);
const organized = ref(false);
const organizeError = ref("");
const candidates = ref<OrganizedExperience[]>([]);
const isEdit = computed(() => route.name === "diary-edit");
const kindOptions = computed(() => library.diaryKinds.map((item) => item.name));
const canSave = computed(
  () => !saving.value && !organizing.value && Boolean(form.title.trim() && form.content.trim() && form.kind.trim()),
);
const canOrganize = computed(() => !saving.value && !organizing.value && Boolean(form.content.trim()));

const form = reactive({
  title: "",
  date: todayISO(),
  kind: "" as DiaryKind,
  tagsText: "",
  summary: "",
  content: "",
});

onMounted(async () => {
  await library.hydrate();
  if (!isEdit.value) return;
  const diary = await library.fetchDiary(String(route.params.id ?? ""));
  if (!diary) {
    ui.toast("找不到要编辑的日记", "error");
    router.replace("/diaries");
    return;
  }
  form.title = diary.title;
  form.date = diary.date;
  form.kind = diary.kind;
  form.tagsText = diary.tags.join(", ");
  form.summary = diary.summary;
  form.content = diary.content;
});

async function organize() {
  if (!canOrganize.value) return;
  organizing.value = true;
  organizeError.value = "";
  try {
    const result = await generateDiary(form.content);
    form.title = result.title;
    form.summary = result.summary ?? "";
    form.content = result.content;
    form.kind = normalizeKind(result.kind || form.kind);
    form.tagsText = (result.tags ?? []).join(", ");
    candidates.value = (result.experiences ?? []).map((item) => ({
      ...item,
      keep: false,
      keepKnowledge: false,
    }));
    organized.value = true;
    ui.toast(candidates.value.length ? "已整理，勾选要留下的经验后再保存" : "已整理日记");
  } catch (error) {
    organizeError.value = errorMessage(error, "整理失败");
    ui.toast(organizeError.value, "error");
  } finally {
    organizing.value = false;
  }
}

async function saveSelectedExperiences(diaryId: string) {
  const selected = candidates.value.filter((item) => item.keep);
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
  if (selected.length) {
    await Promise.all([library.refreshExperiences(), library.refreshKnowledge()]);
  }
}

async function save() {
  if (!canSave.value) return;
  const tags = form.tagsText
    .split(/[,，、\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  const summary = form.summary.trim() || form.content.replace(/[#>*`\-\n]/g, " ").trim().slice(0, 60);
  saving.value = true;
  saveError.value = "";
  try {
    const id = isEdit.value
      ? String(route.params.id)
      : await library.createDiary({
          title: form.title.trim(),
          date: form.date,
          kind: normalizeKind(form.kind),
          tags,
          summary,
          content: form.content,
          origin: "manual",
        });
    if (isEdit.value) {
      await library.updateDiary(id, {
        title: form.title.trim(),
        date: form.date,
        kind: normalizeKind(form.kind),
        tags,
        summary,
        content: form.content,
      });
    }
    try {
      await saveSelectedExperiences(id);
    } catch (error) {
      ui.toast(errorMessage(error, "日记已保存，经验没有写上。可以到详情里再提炼。"), "error");
      router.push(`/diaries/${id}`);
      return;
    }
    const kept = candidates.value.some((item) => item.keep);
    ui.toast(kept ? "日记和勾选的经验已保存" : isEdit.value ? "日记已更新" : "日记已保存");
    router.push(`/diaries/${id}`);
  } catch (error) {
    saveError.value = errorMessage(error, "保存失败");
    ui.toast(saveError.value, "error");
  } finally {
    saving.value = false;
  }
}
</script>
