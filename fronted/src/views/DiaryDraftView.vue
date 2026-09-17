<template>
  <div class="fade-in">
    <div class="toolbar" style="margin-bottom: 12px">
      <button class="crumb" @click="router.push('/interview')">← 回到采访现场</button>
      <span class="spacer" />
      <button class="btn sm" :disabled="!draft" @click="regenerate">重新整理</button>
      <button class="btn sm" :disabled="!draft" @click="discard">丢弃草稿</button>
      <button class="btn sm primary" :disabled="!draft || draft.saved" @click="save">
        {{ draft?.saved ? "已保存" : "保存到日记库" }}
      </button>
    </div>

    <template v-if="draft">
      <div class="panel" style="margin-bottom: 14px">
        <div class="panel-body">
          <div class="row wrap" style="gap: 8px; margin-bottom: 10px">
            <span class="badge">采访草稿</span>
            <span class="small faint">
              {{ draft.date }} · {{ draft.kind }} · 预计沉淀 {{ draft.suggestedKnowledge.length }} 条知识
            </span>
            <span v-if="draft.saved" class="tag accent">已保存为日记</span>
          </div>

          <div class="field">
            <label>标题</label>
            <input v-model="draft.title" class="input" />
          </div>

          <p class="lede" style="margin: 12px 0 0">{{ draft.summary }}</p>
          <TagRow :tags="draft.tags" />
        </div>
      </div>

      <div class="split left-wide">
        <div class="stack">
          <div class="panel">
            <div class="panel-head">
              <b>日记正文</b>
              <span class="spacer" />
              <span class="small faint">可直接编辑，保存后进入日记库</span>
            </div>
            <div class="panel-body">
              <textarea v-model="draft.content" class="textarea" style="min-height: 260px" />
            </div>
          </div>

          <div class="panel ai-block">
            <div class="panel-head"><b>AI 结构化提炼</b></div>
            <div class="panel-body kv">
              <div class="kv-item"><span>问题</span><p>{{ draft.extraction.problem }}</p></div>
              <div class="kv-item"><span>原因</span><p>{{ draft.extraction.cause }}</p></div>
              <div class="kv-item"><span>解决方案</span><p>{{ draft.extraction.solution }}</p></div>
              <div class="kv-item"><span>经验</span><p>{{ draft.extraction.lesson }}</p></div>
            </div>
          </div>
        </div>

        <div class="stack">
          <div class="panel">
            <div class="panel-head">
              <b>建议关联知识</b>
              <span class="spacer" />
              <span class="small faint">命中已有条目则复用</span>
            </div>
            <div class="panel-body stack" style="gap: 8px">
              <div v-for="(item, index) in draft.suggestedKnowledge" :key="index" class="kv-item">
                <span>{{ item.existingId ? "已有知识" : "将新建知识" }}</span>
                <p>{{ item.title }}</p>
                <p class="small faint" style="margin-top: 2px">{{ item.reason }}</p>
              </div>
              <p v-if="!draft.suggestedKnowledge.length" class="small faint">
                这次没有识别出可沉淀的知识条目。
              </p>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head"><b>保存后会发生什么</b></div>
            <div class="panel-body small" style="color: var(--mute)">
              <ul style="margin: 0; padding-left: 18px">
                <li>生成 1 篇日记（来源标记为「采访」）</li>
                <li>从这次对话提炼 1 张经验卡</li>
                <li>复用或新建 {{ draft.suggestedKnowledge.length }} 条知识条目</li>
                <li>全部写入本地 localStorage，不上传任何服务器</li>
              </ul>
              <button class="btn sm primary block" style="margin-top: 10px" :disabled="draft.saved" @click="save">
                {{ draft.saved ? "已保存" : "保存到日记库" }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <EmptyState
      v-else
      icon="✎"
      title="还没有采访草稿"
      hint="先去采访现场和 AI 聊完五步，草稿会自动生成在这里。"
    >
      <button class="btn primary sm" @click="router.push('/interview')">去采访</button>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import EmptyState from "@/components/base/EmptyState.vue";
import TagRow from "@/components/base/TagRow.vue";
import { useInterviewStore } from "@/stores/interview";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const router = useRouter();
const store = useInterviewStore();
const library = useLibraryStore();
const ui = useUiStore();

store.hydrate();

const draft = computed(() => store.draft);

function regenerate() {
  if (!store.draft) return;
  if (store.regenerateDraft()) ui.toast("已按最新回答重新整理草稿");
  else ui.toast("回答还不够，先去采访现场聊完五步", "info");
}

async function save() {
  const target = store.draft;
  if (!target) return;
  if (target.saved && target.savedDiaryId) {
    router.push(`/diaries/${target.savedDiaryId}`);
    return;
  }
  try {
    const created = await library.saveDraft(target);
    store.markDraftSaved(created.diaryId);
    ui.toast(
      `已生成日记，并沉淀 1 张经验卡${created.newKnowledgeIds.length ? `、新建 ${created.newKnowledgeIds.length} 条知识` : ""}`,
    );
    router.push(`/diaries/${created.diaryId}`);
  } catch (error) {
    ui.toast(errorMessage(error, "保存日记失败"), "error");
  }
}

function discard() {
  store.clearDraft();
  ui.toast("已丢弃这次草稿", "info");
  router.push("/interview");
}
</script>
