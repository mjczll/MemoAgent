<template>
  <div class="shell" :class="{ 'shell--collapsed': railCollapsed }">
    <SnowFall />
    <AppRail
      :open="railOpen"
      :collapsed="railCollapsed"
      @navigate="closeRail"
      @toggle-collapse="toggleCollapse"
    />
    <div v-if="railOpen && !railCollapsed" class="scrim" @click="closeRail" />

    <main class="main">
      <AppTopbar @toggle-rail="toggleMobile" :title="titleLabel" :title-editable="titleEditable" />
      <div class="main-inner">
        <div class="main-stack" :class="innerClass">
          <router-view v-slot="{ Component }">
            <component :is="Component" />
          </router-view>
        </div>
      </div>
    </main>

    <ToastHost />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import AppRail from "@/components/shell/AppRail.vue";
import AppTopbar from "@/components/shell/AppTopbar.vue";
import SnowFall from "@/components/base/SnowFall.vue";
import ToastHost from "@/components/base/ToastHost.vue";
import { useAgentStore } from "@/stores/agent";
import { useInterviewStore } from "@/stores/interview";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const railOpen = ref(false);
const railCollapsed = ref(false);

const innerClass = computed(() => {
  const width = route.meta.width as string | undefined;
  if (width === "wide") return "wide";
  if (width === "narrow") return "narrow";
  // Agent / 采访 / 图谱这类需要横向空间的页面给更宽的画布
  if (["agent", "interview", "graph"].includes(String(route.name))) return "wide";
  return "";
});

const titleLabel = computed(() => (route.meta.crumb as string | undefined) ?? "首页");
const titleEditable = computed(() => ["interview", "agent"].includes(String(route.name)));

function toggleMobile() {
  railOpen.value = !railOpen.value;
}

function closeRail() {
  railOpen.value = false;
}

function toggleCollapse() {
  railCollapsed.value = !railCollapsed.value;
  if (!railCollapsed.value) railOpen.value = false;
}

// 切换路由时自动收起移动端侧栏
watch(
  () => route.fullPath,
  () => {
    railOpen.value = false;
  },
);

onMounted(() => {
  useUiStore().hydrate();
  void useLibraryStore().hydrate();
  useInterviewStore().hydrate();
  useAgentStore().hydrate();
});
</script>

<style scoped>
/* 路由切换的 transition 已移除 —— 切换瞬时显示 */
</style>
