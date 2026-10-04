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

      <div class="rail-group">最近</div>
      <div class="thread-list">
        <router-link
          v-for="item in conversations.sessions"
          :key="item.id"
          class="thread-row"
          :class="{ 'is-on': isThread(item.id) }"
          :to="`/c/${item.id}`"
          @click="emit('navigate')"
        >
          <span class="thread-title">{{ item.title }}</span>
          <span class="thread-more" title="删除" @click.prevent.stop="removeThread(item.id)">✕</span>
        </router-link>
      </div>
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
import { useRoute, useRouter } from "vue-router";
import SvgIcon from "@/components/base/SvgIcon.vue";
import { useConversationStore } from "@/stores/conversation";

defineProps<{ open: boolean; collapsed: boolean }>();
const emit = defineEmits<{
  (e: "navigate"): void;
  (e: "toggle-collapse"): void;
}>();

const route = useRoute();
const router = useRouter();
const conversations = useConversationStore();

onMounted(() => {
  void conversations.refresh().catch(() => undefined);
});

interface NavItem {
  to: string;
  label: string;
  icon: "home" | "interview" | "agent" | "diary" | "experience" | "knowledge" | "graph" | "settings";
}

const mainNav = computed<NavItem[]>(() => [
  { to: "/", label: "首页", icon: "home" },
]);

const assetNav = computed<NavItem[]>(() => [
  { to: "/diaries", label: "日记", icon: "diary" },
  { to: "/experiences", label: "经验", icon: "experience" },
  { to: "/knowledge", label: "知识库", icon: "knowledge" },
  { to: "/graph", label: "关系图谱", icon: "graph" },
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

function isThread(id: number) {
  return route.name === "conversation" && String(route.params.id) === String(id);
}

async function removeThread(id: number) {
  await conversations.remove(id);
  if (isThread(id)) {
    await router.push("/");
  }
}
</script>