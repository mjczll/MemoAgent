<template>
  <aside class="rail" :class="{ open }">
    <button
      class="rail-fold"
      :title="collapsed ? '展开侧栏' : '折叠侧栏'"
      @click="emit('toggle-collapse')"
    >
      <i />
    </button>

    <div class="rail-head">
      <router-link class="brand" to="/" @click="emit('navigate')">
        <span class="brand-mark">MA</span>
        <span class="brand-text">
          <b>MemoAgent</b>
          <span>把经历沉淀成可复用的知识</span>
        </span>
      </router-link>
    </div>

    <div class="rail-scroll">
      <div class="rail-group">创作</div>
      <nav class="nav">
        <router-link
          v-for="item in mainNav"
          :key="item.to"
          class="nav-item"
          :class="{ active: isActive(item) }"
          :to="item.to"
          @click="emit('navigate')"
        >
          <span class="nav-ico">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="count-pill">{{ item.badge }}</span>
          <span v-else-if="item.hint" class="nav-sub">{{ item.hint }}</span>
        </router-link>
      </nav>

      <!-- 最近会话：复用 DealEats ThreadRow 风格 -->
      <template v-if="recentThreads.length">
        <div class="rail-group">最近会话</div>
        <nav class="thread-list">
          <div
            v-for="t in recentThreads"
            :key="t.id"
            class="thread-row"
            :class="{ 'is-on': isThreadActive(t) }"
            @click="openThread(t)"
          >
            <span class="thread-title">{{ t.title }}</span>
            <span class="thread-meta">{{ t.meta }}</span>
          </div>
        </nav>
      </template>

      <div class="rail-group">我的资产</div>
      <nav class="nav">
        <router-link
          v-for="item in assetNav"
          :key="item.to"
          class="nav-item"
          :class="{ active: isActive(item) }"
          :to="item.to"
          @click="emit('navigate')"
        >
          <span class="nav-ico">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="count-pill">{{ item.badge }}</span>
          <span v-else-if="item.hint" class="nav-sub">{{ item.hint }}</span>
        </router-link>
      </nav>
    </div>

    <div class="rail-foot">
      <router-link class="nav-item" to="/settings" @click="emit('navigate')">
        <span class="nav-ico">⚙</span>
        <span>设置</span>
      </router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAgentStore } from "@/stores/agent";
import { useInterviewStore } from "@/stores/interview";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

defineProps<{ open: boolean; collapsed: boolean }>();
const emit = defineEmits<{
  (e: "navigate"): void;
  (e: "toggle-collapse"): void;
}>();

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();
const interview = useInterviewStore();
const agent = useAgentStore();

const stats = computed(() => library.stats);

interface NavItem {
  to: string;
  label: string;
  icon: string;
  badge?: string;
  hint?: string;
}

const mainNav = computed<NavItem[]>(() => [
  { to: "/", label: "首页", icon: "⌂" },
  { to: "/interview", label: "开始采访", icon: "◍" },
  { to: "/agent", label: "问 Agent", icon: "✦" },
]);

const assetNav = computed<NavItem[]>(() => [
  { to: "/diaries", label: "日记", icon: "▤", badge: String(library.diaries.length) },
  { to: "/experiences", label: "经验", icon: "◆", badge: String(library.experiences.length) },
  { to: "/knowledge", label: "知识库", icon: "●", badge: String(library.knowledge.length) },
  { to: "/graph", label: "知识织网", icon: "⁂", badge: String(library.stats.linkCount) },
]);

/** 最近会话：当前采访 / 最近 Agent 问题 */
const recentThreads = computed(() => {
  const items: Array<{ id: string; title: string; meta: string; route: string; kind: "interview" | "agent" }> = [];
  if (interview.opening) {
    items.push({
      id: "interview-current",
      title: truncateTitle(interview.opening, 18) || "采访进行中",
      meta: "采访",
      route: "/interview",
      kind: "interview",
    });
  }
  if (agent.lastQuestion) {
    items.push({
      id: "agent-last",
      title: truncateTitle(agent.lastQuestion, 18),
      meta: "Agent",
      route: "/agent",
      kind: "agent",
    });
  }
  return items;
});

function isActive(item: { to: string }): boolean {
  if (item.to === "/") return route.path === "/";
  if (item.to === "/diaries") {
    return (
      route.path === "/diaries" ||
      (route.path.startsWith("/diaries/") && route.path !== "/diaries/new")
    );
  }
  return route.path === item.to || route.path.startsWith(`${item.to}/`);
}

function isThreadActive(t: { route: string; kind: string }): boolean {
  if (route.path !== t.route) return false;
  if (t.kind === "interview") return Boolean(interview.opening);
  if (t.kind === "agent") return Boolean(agent.lastQuestion);
  return false;
}

function openThread(t: { route: string }) {
  router.push(t.route);
  emit("navigate");
}

function truncateTitle(text: string, max: number): string {
  if (!text) return "";
  return text.length > max ? text.slice(0, max - 1) + "…" : text;
}
</script>