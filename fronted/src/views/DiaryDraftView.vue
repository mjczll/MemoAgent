<template>
  <div class="fade-in">
    <span class="crumb" @click="goBack">← 返回</span>
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">整理草稿</h1>
        <p class="lede" style="margin-bottom: 0">
          {{
            draft?.incident
              ? autoExtract
                ? "确认标题和正文。保存后会出现在日记列表，并提炼一张经验。"
                : "确认标题和正文。保存后会出现在日记列表；未开启「自动结构化提炼」，不会生成经验卡。"
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
              <label>领域</label>
              <SuggestCombo
                :model-value="domainValue"
                :options="domainOptions"
                :maxlength="64"
                placeholder="选择已有领域，或输入新领域"
                aria-label="选择领域"
                @update:model-value="onDomainChange"
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

        <div v-if="draft.incident && autoExtract" class="panel">
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
              <input
                v-model="selectedRelated[relationKey(item)]"
                type="checkbox"
                :disabled="item.type === 'knowledge' && !autoExtract"
              />
              <span class="small">{{ relationLabel(item.type) }} · {{ item.title }}</span>
            </label>
            <p v-if="relatedLoading" class="small faint">正在查找相关记录…</p>
            <p v-else-if="!related.length" class="small faint">没有对上已有的日记、经验或知识。保存后只会留下这次新写的日记和经验。</p>
            <p v-if="!autoExtract && hasKnowledgeHit" class="small faint" style="margin: 0">
              未开启「采访结束后自动结构化提炼」，不会生成经验卡，知识无法挂关联；勾选日记或经验仍会把它们挂到这篇日记上。
            </p>
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
import { fetchRelated, type RecallHit } from "@/api/conversation";
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
/** 领域可编辑值：初始取草稿自带的领域；职责归经验——这里只是人工修正入口 */
const domainText = ref("");

/**
 * 返回来路：从草稿中心进来就回草稿中心，从对话页进来回对话页；
 * 直链打开（无应用内历史）时兜底到草稿中心——草稿页本来就是它的一部分。
 */
function goBack() {
  const back = window.history.state?.back;
  if (back) router.back();
  else router.push("/drafts");
}
watch(
  () => store.current?.draft?.domain,
  (value) => {
    if (domainText.value === "" && value) {
      domainText.value = value;
    }
  },
  { immediate: true },
);
const domainValue = computed(() => domainText.value || store.current?.draft?.domain || "");
const domainOptions = computed(() =>
  Object.keys(library.domainCounts)
    .filter((name) => name && name !== "undefined")
    .sort()
    .map((name) => ({ value: name, label: name })),
);
function onDomainChange(value: string) {
  domainText.value = value;
}
const selectedRelated = ref<Record<string, boolean>>({});

const draft = computed(() => store.current?.draft ?? null);
/** 自动结构化提炼开关（设置页偏好）：关闭时整理只落日记、不产经验卡 */
const autoExtract = computed(() => ui.profile.autoExtract);
const saved = computed(() => store.current?.status === "committed");
/** 相关记录由独立接口异步加载（语义召回要过 embedding 模型，不能挡详情首屏） */
const relatedList = ref<RecallHit[]>([]);
const relatedLoading = ref(false);
const related = computed(() => relatedList.value);
/** 召回到知识项且提炼关闭：这批勾选不生效，需要给用户一句解释 */
const hasKnowledgeHit = computed(() => related.value.some((item) => item.type === "knowledge"));

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
      if (next[key] === undefined) {
        // 关掉提炼时没有经验载体，知识项默认不勾（勾了也存不进去），日记/经验照常默认勾选
        next[key] = item.type !== "knowledge" || autoExtract.value;
      }
    }
    selectedRelated.value = next;
  },
  { immediate: true },
);

onMounted(async () => {
  // Pinia 的 current 是全局残留：不清掉的话，新草稿加载完成前会先渲染出上一份草稿
  store.current = null;
  // 资产列表只在保存时做关联回填用，不该挡住草稿渲染：与 open 并行、失败不阻塞
  const hydratePromise = library.hydrate().catch(() => undefined);
  const id = String(route.params.id ?? "");
  try {
    await store.open(id);
  } catch (error) {
    ui.toast(errorMessage(error, "草稿加载失败"), "error");
  } finally {
    loading.value = false;
  }
  loadRelated(id);
  await hydratePromise;
});

/** 相关记录异步补齐：失败静默，面板回落到「没有对上」文案 */
function loadRelated(id: string) {
  relatedLoading.value = true;
  fetchRelated(id)
    .then((hits) => {
      relatedList.value = hits;
    })
    .catch(() => undefined)
    .finally(() => {
      relatedLoading.value = false;
    });
}

async function regenerate() {
  try {
    await store.draft();
    if (store.current) loadRelated(String(store.current.id));
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
    // 提炼关闭时没有经验卡承接知识关联，即便勾选状态残留也不提交
    if (item.type === "knowledge" && !autoExtract.value) continue;
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
      domain: domainValue.value,
      tags,
      problem: autoExtract.value ? target.problem : "",
      cause: autoExtract.value ? target.cause : "",
      solution: autoExtract.value ? target.solution : "",
      lesson: autoExtract.value ? target.lesson : "",
      knowledgeIds,
      experienceIds,
    });
    await library.hydrate();
    if (created.diaryId) {
      ui.toast(created.experienceId ? "已保存日记和经验" : "已保存日记");
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
