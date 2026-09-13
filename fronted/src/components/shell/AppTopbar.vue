<template>
  <header class="topbar">
    <button class="icon-btn menu-btn" title="菜单" @click="emit('toggle-rail')">☰</button>

    <div class="topbar-spacer">
      <!-- Interview / Agent 路由下显示可点击重命名的聊天标题栏 -->
      <div v-if="titleEditable" class="chat-title-bar">
        <template v-if="editing">
          <div class="chat-title-form">
            <input ref="titleInput" v-model="draft" @keydown.enter="commit" @keydown.esc="cancel" />
            <button class="btn sm" @click="cancel">取消</button>
            <button class="btn sm primary" @click="commit">保存</button>
          </div>
        </template>
        <button v-else class="chat-title-btn" @click="startEdit" :title="title">
          <span>{{ title }}</span>
          <svg width="12" height="12" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2 14l2-1 8-8-1-1-8 8zM10 3l3 3" stroke="currentColor" fill="none" stroke-width="1.4" />
          </svg>
        </button>
      </div>
    </div>

    <span class="spacer" />
  </header>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useInterviewStore } from "@/stores/interview";
import { useAgentStore } from "@/stores/agent";
import { useUiStore } from "@/stores/ui";

const props = defineProps<{
  title?: string;
  titleEditable?: boolean;
}>();

const emit = defineEmits<{ (e: "toggle-rail"): void }>();

const route = useRoute();
const router = useRouter();
const ui = useUiStore();
const interview = useInterviewStore();
const agent = useAgentStore();
const keyword = ref("");

const editing = ref(false);
const draft = ref("");
const titleInput = ref<HTMLInputElement | null>(null);

const liveTitle = computed(() => {
  if (!props.titleEditable) return props.title ?? "";
  if (route.name === "interview") {
    return interview.opening ? truncate(interview.opening, 18) : "采访现场";
  }
  if (route.name === "agent") {
    return agent.lastQuestion ? truncate(agent.lastQuestion, 18) : "Agent 问答";
  }
  return props.title ?? "";
});

const title = computed(() => props.title ?? liveTitle.value);

function truncate(text: string, max: number): string {
  if (!text) return "";
  return text.length > max ? text.slice(0, max - 1) + "…" : text;
}

function startEdit() {
  draft.value = title.value;
  editing.value = true;
  nextTick(() => titleInput.value?.focus());
}

function cancel() {
  editing.value = false;
  draft.value = "";
}

function commit() {
  const text = draft.value.trim();
  if (!text) {
    cancel();
    return;
  }
  if (route.name === "interview") interview.reset(text);
  else if (route.name === "agent") {
    agent.renameActive(text);
    agent.ask(text);
  }
  editing.value = false;
  ui.toast("已重命名当前会话");
}

// 切路由退出编辑态
watch(
  () => route.fullPath,
  () => {
    editing.value = false;
  },
);
</script>
