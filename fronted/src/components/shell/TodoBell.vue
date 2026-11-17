<template>
  <div ref="root" class="todo-bell">
    <button
      type="button"
      class="bell-btn"
      :class="{ 'has-todos': todos.length > 0 }"
      title="待办通知"
      @click="toggle"
    >
      <svg width="17" height="17" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 3a6 6 0 0 0-6 6v3.2c0 .6-.2 1.2-.6 1.7L4 16h16l-1.4-2.1a3 3 0 0 1-.6-1.7V9a6 6 0 0 0-6-6z"
          fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"
        />
        <path d="M9.8 19a2.3 2.3 0 0 0 4.4 0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
      <span v-if="todos.length" class="badge">{{ todos.length }}</span>
    </button>

    <transition name="todo-pop">
      <div v-if="open" class="todo-panel">
        <div class="todo-head">
          <b>待办通知</b>
          <span class="spacer" />
          <button class="todo-refresh" :disabled="loading" @click="load">{{ loading ? "刷新中…" : "刷新" }}</button>
        </div>

        <div v-if="error" class="todo-body todo-faint">加载失败：{{ error }}</div>
        <div v-else-if="loading && !loadedOnce" class="todo-body todo-faint">正在获取待办…</div>
        <div v-else-if="!todos.length" class="todo-body todo-faint">
          暂无待办——草稿待确认和每周回顾到期后会出现在这里。
        </div>

        <template v-else>
          <!-- 草稿待确认：整行可点，跳草稿中心 -->
          <button
            v-for="item in todos"
            :key="item.type"
            type="button"
            class="todo-item"
            :class="{ clickable: item.type === 'drafts' }"
            @click="item.type === 'drafts' ? goDrafts() : undefined"
          >
            <span class="todo-dot" :class="item.type" />
            <span class="todo-main">
              <span class="todo-title">{{ item.title }}</span>
              <span class="todo-desc">
                {{ item.description }}
                <template v-if="item.scheduledAt"> · {{ formatTime(item.scheduledAt) }}</template>
              </span>
              <span v-if="item.type === 'weekly-review'" class="todo-action-row">
                <button
                  class="todo-action"
                  :disabled="generating"
                  @click.stop="generateReview"
                >
                  {{ generating ? "正在生成…" : "生成本周回顾草稿" }}
                </button>
              </span>
            </span>
            <span v-if="item.type === 'drafts'" class="todo-go">›</span>
          </button>
        </template>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { fetchTodos } from "@/api/todos";
import { generateWeeklyReview } from "@/api/conversation";
import { useUiStore } from "@/stores/ui";
import type { TodoItem } from "@/types";

const route = useRoute();
const router = useRouter();
const ui = useUiStore();

const root = ref<HTMLElement | null>(null);
const open = ref(false);
const loading = ref(false);
const loadedOnce = ref(false);
const error = ref("");
const todos = ref<TodoItem[]>([]);
const generating = ref(false);

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    todos.value = await fetchTodos();
    error.value = "";
    loadedOnce.value = true;
  } catch (err) {
    if (!silent) error.value = errorMessage(err, "待办加载失败");
  } finally {
    if (!silent) loading.value = false;
  }
}

function toggle() {
  open.value = !open.value;
  if (open.value) load();
}

function goDrafts() {
  open.value = false;
  router.push("/drafts");
}

async function generateReview() {
  if (generating.value) return;
  generating.value = true;
  try {
    const id = await generateWeeklyReview();
    ui.toast("本周回顾草稿已生成，请在草稿中心确认");
    open.value = false;
    await load(true);
    router.push(`/diaries/draft/${id}`);
  } catch (err) {
    ui.toast(errorMessage(err, "回顾生成失败"), "error");
    load(true);
  } finally {
    generating.value = false;
  }
}

function formatTime(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function onOutside(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) {
    open.value = false;
  }
}

// 路由切换时静默刷新角标（待办是查询式动态计算的，到点即出现）
watch(
  () => route.fullPath,
  () => {
    if (loadedOnce.value) load(true);
  },
);

onMounted(() => {
  document.addEventListener("mousedown", onOutside);
  load(true);
});

onBeforeUnmount(() => {
  document.removeEventListener("mousedown", onOutside);
});
</script>

<style scoped>
.todo-bell {
  position: relative;
  display: flex;
  align-items: center;
}
.bell-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  background: none;
  color: var(--mute);
  cursor: pointer;
  border-radius: 8px;
  padding: 0;
}
.bell-btn:hover,
.bell-btn.has-todos {
  color: var(--accent);
}
.badge {
  position: absolute;
  top: -2px;
  right: -4px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--danger);
  color: var(--paper);
  font-size: 10px;
  font-weight: 700;
  line-height: 15px;
  text-align: center;
}
.todo-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  max-height: 380px;
  overflow-y: auto;
  background: var(--paper-2);
  border: 1px solid var(--line-2);
  border-radius: 12px;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.35);
  z-index: 60;
}
.todo-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.todo-refresh {
  border: none;
  background: none;
  color: var(--mute);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.todo-refresh:hover:not(:disabled) {
  color: var(--accent);
}
.todo-refresh:disabled {
  opacity: 0.6;
  cursor: default;
}
.todo-body {
  padding: 14px;
  font-size: 12.5px;
}
.todo-faint {
  color: var(--mute);
}
.todo-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: none;
  border-bottom: 1px solid var(--line);
  background: none;
  color: inherit;
  text-align: left;
  font-size: 12.5px;
  cursor: default;
}
.todo-item:last-child {
  border-bottom: none;
}
.todo-item.clickable {
  cursor: pointer;
}
.todo-item.clickable:hover {
  background: var(--accent-soft);
}
.todo-dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 5px;
  background: var(--mute);
}
.todo-dot.weekly-review {
  background: var(--warn);
}
.todo-dot.drafts {
  background: var(--accent);
}
.todo-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.todo-title {
  font-weight: 600;
  font-size: 13px;
}
.todo-desc {
  color: var(--mute);
  line-height: 1.45;
}
.todo-action-row {
  margin-top: 6px;
}
.todo-action {
  border: 1px solid var(--accent-line);
  background: none;
  color: var(--accent);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
}
.todo-action:hover:not(:disabled) {
  background: var(--accent-soft);
}
.todo-action:disabled {
  opacity: 0.6;
  cursor: default;
}
.todo-go {
  color: var(--mute);
  font-size: 16px;
  align-self: center;
}
.todo-pop-enter-active,
.todo-pop-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}
.todo-pop-enter-from,
.todo-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
