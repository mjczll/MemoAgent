<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">草稿中心</h1>
        <p class="lede" style="margin-bottom: 0">
          {{
            error
              ? "后端数据不可用，列表未加载。"
              : `AI 生成的草稿确认提交后才会保存为日记 / 经验 / 知识：包括采访整理、复盘整理和自动生成的本周回顾。`
          }}
        </p>
      </div>
      <button class="btn" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="panel">
      <div class="panel-head">
        <b>{{ error ? "加载失败" : `共 ${drafts.length} 份待确认` }}</b>
        <span class="spacer" />
        <span class="small faint">{{
          error
            ? "后端数据不可用，以下内容不完整"
            : loading
              ? "正在从后端加载…"
              : "点击任意一条进入草稿确认页"
        }}</span>
      </div>

      <div v-if="error" class="panel-body">
        <EmptyState icon="⚠" title="草稿加载失败" :hint="error">
          <button class="btn primary sm" @click="load">重试</button>
        </EmptyState>
      </div>
      <div v-else class="list">
        <div
          v-for="item in drafts"
          :key="item.id"
          class="list-row"
          @click="router.push(`/diaries/draft/${item.id}`)"
        >
          <div class="title">
            <b>{{ item.title }}</b>
            <span v-if="isAutoReview(item)" class="tag accent origin-inline">自动回顾</span>
          </div>
          <div class="meta">
            <span>{{ relative(item.updatedAt) }}更新</span>
            <span>{{ sourceLabel(item) }}</span>
          </div>
        </div>
      </div>
      <EmptyState
        v-if="!loading && !error && !drafts.length"
        icon="✓"
        title="没有待确认的草稿"
        hint="采访或复盘整理后生成的草稿会出现在这里。"
      >
        <button class="btn primary sm" @click="router.push('/')">去开始一次对话</button>
      </EmptyState>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { useConversationStore } from "@/stores/conversation";
import type { ConversationListItem } from "@/api/conversation";
import EmptyState from "@/components/base/EmptyState.vue";
import { relative } from "@/lib/format";
import { AUTO_REVIEW_TITLE_PREFIX } from "@/lib/constants";

const router = useRouter();
const conversations = useConversationStore();

const loading = ref(false);
const error = ref("");

const drafts = computed(() =>
  conversations.sessions.filter((item) => item.status === "drafted"),
);

function isAutoReview(item: ConversationListItem) {
  return item.title.startsWith(AUTO_REVIEW_TITLE_PREFIX);
}

function sourceLabel(item: ConversationListItem) {
  return isAutoReview(item) ? "来自每周回顾待办" : "来自对话整理";
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    await conversations.refresh();
  } catch (err) {
    error.value = errorMessage(err, "草稿列表加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
/* 与日记页一致：来源徽标紧跟标题文字 */
.origin-inline {
  margin-left: 8px;
  vertical-align: 2px;
  white-space: nowrap;
}
</style>
