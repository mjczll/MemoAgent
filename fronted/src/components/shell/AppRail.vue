<template>
  <aside class="rail" :class="{ open, collapsed }">
    <!-- 顶部品牌区：纯展示 -->
    <div class="rail-head">
      <span class="brand-text">
        <b>MemoAgent</b>
        <span>把经历沉淀成可复用的知识</span>
      </span>
      <!-- 折叠/展开按钮：贴在侧栏右边缘 -->
      <button
        class="rail-toggle-btn"
        :title="collapsed ? '展开侧栏' : '折叠侧栏'"
        @click="emit('toggle-collapse')"
      >
        <SvgIcon :name="collapsed ? 'rail-expand' : 'rail-collapse'" />
      </button>
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
          <SvgIcon class="nav-ico" :name="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

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
          <SvgIcon class="nav-ico" :name="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </div>

    <div class="rail-foot">
      <router-link class="nav-item" to="/settings" @click="emit('navigate')">
        <SvgIcon class="nav-ico" name="settings" />
        <span>设置</span>
      </router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { useAgentStore } from "@/stores/agent";
import { useInterviewStore } from "@/stores/interview";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import SvgIcon from "@/components/base/SvgIcon.vue";

defineProps<{ open: boolean; collapsed: boolean }>();
const emit = defineEmits<{
  (e: "navigate"): void;
  (e: "toggle-collapse"): void;
}>();

const route = useRoute();
const library = useLibraryStore();
const ui = useUiStore();
const interview = useInterviewStore();
const agent = useAgentStore();

onMounted(() => {
  interview.hydrate();
  agent.hydrate();
});

const stats = computed(() => library.stats);

interface NavItem {
  to: string;
  label: string;
  icon: "home" | "interview" | "agent" | "diary" | "experience" | "knowledge" | "graph" | "settings";
}

const mainNav = computed<NavItem[]>(() => [
  { to: "/", label: "首页", icon: "home" },
  { to: "/interview", label: "开始采访", icon: "interview" },
  { to: "/agent", label: "问 Agent", icon: "agent" },
]);

const assetNav = computed<NavItem[]>(() => [
  { to: "/diaries", label: "日记", icon: "diary" },
  { to: "/experiences", label: "经验", icon: "experience" },
  { to: "/knowledge", label: "知识库", icon: "knowledge" },
  { to: "/graph", label: "知识织网", icon: "graph" },
]);

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
</script>