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
              <select v-model="form.kind" class="select" :disabled="!kindOptions.length">
                <option v-for="k in kindOptions" :key="k" :value="k">{{ k }}</option>
              </select>
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
import MarkdownView from "@/components/base/MarkdownView.vue";
import { todayISO } from "@/lib/format";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { DiaryKind } from "@/types";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const saving = ref(false);
const saveError = ref("");
const isEdit = computed(() => route.name === "diary-edit");
const kindOptions = computed(() => {
  const names = library.diaryKinds.map((item) => item.name);
  if (form.kind && !names.includes(form.kind)) return [form.kind, ...names];
  return names;
});
const canSave = computed(
  () => !saving.value && Boolean(form.title.trim() && form.content.trim() && form.kind),
);

const form = reactive({
  title: "",
  date: todayISO(),
  kind: "技术" as DiaryKind,
  tagsText: "",
  summary: "",
  content: "",
});

onMounted(async () => {
  await library.hydrate();
  if (!isEdit.value) {
    form.kind = library.defaultDiaryKind;
    return;
  }
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
    if (isEdit.value) {
      const id = String(route.params.id);
      await library.updateDiary(id, {
        title: form.title.trim(),
        date: form.date,
        kind: form.kind,
        tags,
        summary,
        content: form.content,
      });
      ui.toast("日记已更新");
      router.push(`/diaries/${id}`);
      return;
    }
    const id = await library.createDiary({
      title: form.title.trim(),
      date: form.date,
      kind: form.kind,
      tags,
      summary,
      content: form.content,
      origin: "manual",
    });
    ui.toast("日记已保存");
    router.push(`/diaries/${id}`);
  } catch (error) {
    saveError.value = errorMessage(error, "保存失败");
    ui.toast(saveError.value, "error");
  } finally {
    saving.value = false;
  }
}
</script>
