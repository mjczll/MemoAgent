<template>
  <div class="fade-in">
    <span class="crumb" @click="router.push('/')">← 返回</span>
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">整理草稿</h1>
        <p class="lede" style="margin-bottom: 0">
          {{
            draft?.incident
              ? "确认标题和正文。保存后会出现在日记列表，并提炼一张经验。"
              : "这次没有具体经历。保存后不生成日记和经验，只写入你点过同意的补充和已确认的关联。"
          }}
        </p>
      </div>
      <button type="button" class="btn" :disabled="!draft" @click="regenerate">重新整理</button>
      <button type="button" class="btn" :disabled="!draft" @click="discard">丢弃</button>
      <button type="button" class="btn primary" :disabled="!draft || saved" @click="save">
        {{ saved ? "已保存" : "保存" }}
      </button>
    </div>

    <div v-if="draft" class="split">
      <div class="panel">
        <div class="panel-head">
          <b>正文</b>
          <span class="spacer" />
          <span class="small faint">支持 Markdown 与 [[双链]]</span>
        </div>
        <div class="panel-body stack">
          <div class="field">
            <label>标题</label>
            <input v-model="draft.title" class="input" placeholder="例如：一次 Nginx 502 的排查记录" />
          </div>
          <div class="field">
            <label>正文</label>
            <textarea v-model="draft.content" class="textarea" style="min-height: 320px" placeholder="## 背景&#10;&#10;发生了什么…&#10;&#10;## 解决&#10;&#10;…" />
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>元信息</b></div>
          <div class="panel-body stack">
            <div class="field">
              <label>日期</label>
              <input v-model="draft.date" type="date" class="input" />
            </div>
            <div class="field">
              <label>类型</label>
              <SuggestCombo
                v-model="draft.kind"
                :options="kindOptions"
                :maxlength="32"
                placeholder="选择已有类型，或输入新类型"
                aria-label="选择已有类型"
              />
            </div>
            <div class="field">
              <label>标签（逗号分隔）</label>
              <input v-model="tagsText" class="input" placeholder="Redis, 缓存, 排查" />
            </div>
            <div class="field">
              <label>一句话摘要</label>
              <textarea v-model="draft.summary" class="textarea" style="min-height: 72px" />
            </div>
            <div class="row small faint">
              <span>{{ draft.content.length }} 字</span>
              <span>·</span>
              <span>预计 {{ Math.max(1, Math.round(draft.content.length / 220)) }} 分钟读完</span>
            </div>
          </div>
        </div>

        <div v-if="draft.incident" class="panel">
          <div class="panel-head"><b>经验</b></div>
          <div class="panel-body stack">
            <div class="field">
              <label>问题</label>
              <textarea :value="editable(draft.problem)" class="textarea" style="min-height: 64px" placeholder="待补充" @input="draft.problem = fieldValue($event)" />
            </div>
            <div class="field">
              <label>原因</label>
              <textarea :value="editable(draft.cause)" class="textarea" style="min-height: 64px" placeholder="待补充" @input="draft.cause = fieldValue($event)" />
            </div>
            <div class="field">
              <label>解决方案</label>
              <textarea :value="editable(draft.solution)" class="textarea" style="min-height: 64px" placeholder="待补充" @input="draft.solution = fieldValue($event)" />
            </div>
            <div class="field">
              <label>经验</label>
              <textarea :value="editable(draft.lesson)" class="textarea" style="min-height: 64px" placeholder="待补充" @input="draft.lesson = fieldValue($event)" />
            </div>
          </div>
        </div>

        <div v-if="draft.incident" class="panel">
          <div class="panel-head"><b>相关记录</b></div>
          <div class="panel-body stack" style="gap: 8px">
            <label v-for="item in related" :key="`${item.type}-${item.id}`" class="row" style="align-items: flex-start">
              <input v-model="selectedRelated[relationKey(item)]" type="checkbox" />
              <span class="small">{{ relationLabel(item.type) }} · {{ item.title }}</span>
            </label>
            <p v-if="!related.length" class="small faint">没有对上已有的日记、经验或知识。保存后只会留下这次新写的日记和经验。</p>
          </div>
        </div>

        <div class="panel ai-block">
          <div class="panel-head"><b>预览</b></div>
          <div class="panel-body">
            <MarkdownView :text="draft.content || '*还没有内容*'" />
          </div>
        </div>
      </div>
    </div>

    <EmptyState
      v-else-if="!loading"
      icon="✎"
      title="还没有采访草稿"
      hint="回到首页，讲完后点「整理」。"
    >
      <button class="btn primary sm" @click="router.push('/')">回到首页</button>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import type { RecallHit } from "@/api/conversation";
import EmptyState from "@/components/base/EmptyState.vue";
import MarkdownView from "@/components/base/MarkdownView.vue";
import SuggestCombo from "@/components/base/SuggestCombo.vue";
import { useConversationStore } from "@/stores/conversation";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const router = useRouter();
const store = useConversationStore();
const library = useLibraryStore();
const ui = useUiStore();
const loading = ref(true);
const tagsText = ref("");
const selectedRelated = ref<Record<string, boolean>>({});

const draft = computed(() => store.current?.draft ?? null);
const saved = computed(() => store.current?.status === "committed");
const kindOptions = computed(() => library.diaryKinds.map((item) => item.name));
const related = computed(() => store.current?.related ?? []);

function relationKey(item: RecallHit) {
  return `${item.type}:${item.id}`;
}

function relationLabel(type: RecallHit["type"]) {
  if (type === "diary") return "日记";
  if (type === "experience") return "经验";
  return "知识";
}

function editable(value: string) {
  return value === "待补充" ? "" : value;
}

function fieldValue(event: Event) {
  return (event.target as HTMLTextAreaElement).value;
}

watch(
  draft,
  (value) => {
    tagsText.value = value?.tags.join(", ") ?? "";
  },
  { immediate: true },
);

watch(
  related,
  (items) => {
    const next = { ...selectedRelated.value };
    for (const item of items) {
      const key = relationKey(item);
      if (next[key] === undefined) next[key] = true;
    }
    selectedRelated.value = next;
  },
  { immediate: true },
);

onMounted(async () => {
  try {
    await library.hydrate();
    await store.open(String(route.params.id ?? ""));
  } catch (error) {
    ui.toast(errorMessage(error, "草稿加载失败"), "error");
  } finally {
    loading.value = false;
  }
});

async function regenerate() {
  try {
    await store.draft();
    ui.toast("已按最新对话重新整理");
  } catch (error) {
    ui.toast(errorMessage(error, "整理失败"), "error");
  }
}

async function save() {
  const target = draft.value;
  if (!target || !store.current) return;
  if (saved.value && store.current.committedDiaryId) {
    router.push(`/diaries/${store.current.committedDiaryId}`);
    return;
  }
  const tags = tagsText.value
    .split(/[,，、\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  const knowledgeIds: number[] = [];
  const experienceIds: number[] = [];
  for (const item of related.value) {
    if (!selectedRelated.value[relationKey(item)]) continue;
    if (item.type === "knowledge") knowledgeIds.push(item.id);
    if (item.type === "experience") experienceIds.push(item.id);
    if (item.type === "diary") {
      library.experiences
        .filter((experience) => experience.diaryId === String(item.id))
        .forEach((experience) => experienceIds.push(Number(experience.id)));
    }
  }
  try {
    const created = await store.commit({
      title: target.title,
      date: target.date,
      summary: target.summary,
      content: target.content,
      kind: target.kind,
      tags,
      problem: target.problem,
      cause: target.cause,
      solution: target.solution,
      lesson: target.lesson,
      knowledgeIds,
      experienceIds,
    });
    await library.hydrate();
    if (created.diaryId) {
      ui.toast("已保存日记和经验");
      router.push(`/diaries/${created.diaryId}`);
      return;
    }
    ui.toast(`已写入 ${created.knowledgeIds.length} 条知识`);
    router.push(created.knowledgeIds[0] ? `/knowledge/${created.knowledgeIds[0]}` : "/knowledge");
  } catch (error) {
    ui.toast(errorMessage(error, "保存失败"), "error");
  }
}

function discard() {
  ui.toast("草稿还在这次对话里，可以回到对话继续", "info");
  router.push("/");
}
</script>
