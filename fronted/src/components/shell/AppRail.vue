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
          :class="{ 'is-on': isThread(item.id), 'is-editing': editingId === item.id }"
          :to="`/c/${item.id}`"
          :draggable="editingId === item.id ? false : undefined"
          @click="onRowClick(item, $event)"
          @dragstart="onDragStart(item, $event)"
        >
          <span v-if="item.pinned" class="thread-pin" aria-label="已置顶" />
          <input
            v-if="editingId === item.id"
            :data-rename="item.id"
            v-model="editingTitle"
            class="thread-rename"
            maxlength="200"
            @click.prevent.stop
            @dragover.prevent
            @drop.prevent
            @keyup.enter="commitRename"
            @keyup.esc="cancelRename"
            @blur="commitRename"
          />
          <span v-else class="thread-title">{{ item.title }}</span>
          <button
            v-if="editingId !== item.id"
            type="button"
            class="thread-more"
            title="更多操作"
            aria-label="更多操作"
            @click.prevent.stop="openMenu(item, $event)"
          >
            ⋯
          </button>
        </router-link>
        <p v-if="!conversations.sessions.length" class="thread-empty">还没有对话</p>
      </div>
    </div>

    <div class="rail-foot">
      <router-link class="nav-item" to="/settings" @click="emit('navigate')">
        <SvgIcon class="nav-ico" name="settings" />
        <span>设置</span>
      </router-link>
    </div>
  </aside>

  <Teleport to="body">
    <div
      v-if="menuItem"
      class="thread-menu"
      :style="{ top: `${menuPos.top}px`, left: `${menuPos.left}px` }"
    >
      <button type="button" @click="startRename">重命名</button>
      <button type="button" @click="togglePin">{{ menuItem.pinned ? "取消置顶" : "置顶" }}</button>
      <button type="button" class="danger" @click="askDelete">删除</button>
    </div>
  </Teleport>

  <ModalDialog v-model="deleteOpen" title="删除后，该对话将不可恢复" width="420px">
    <template #foot>
      <button class="btn" @click="deleteOpen = false">取消</button>
      <button class="btn danger" :disabled="busy" @click="confirmDelete">删除对话</button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { ConversationListItem } from "@/api/conversation";
import { errorMessage } from "@/api/http";
import ModalDialog from "@/components/base/ModalDialog.vue";
import SvgIcon from "@/components/base/SvgIcon.vue";
import { useConversationStore } from "@/stores/conversation";
import { useUiStore } from "@/stores/ui";

defineProps<{ open: boolean; collapsed: boolean }>();
const emit = defineEmits<{
  (e: "navigate"): void;
  (e: "toggle-collapse"): void;
}>();

const route = useRoute();
const router = useRouter();
const conversations = useConversationStore();
const ui = useUiStore();

/** 菜单宽度，与 CSS 里的 .thread-menu 保持一致，用于右边缘对齐 */
const MENU_WIDTH = 132;

const menuItem = ref<ConversationListItem | null>(null);
const menuPos = ref({ top: 0, left: 0 });
const editingId = ref<number | null>(null);
const editingTitle = ref("");
const deleteOpen = ref(false);
const deleteTarget = ref<ConversationListItem | null>(null);
const busy = ref(false);
/** Esc 取消时先置位，避免随后的 blur 又提交一次 */
let skipBlur = false;

onMounted(() => {
  void conversations.refresh().catch(() => undefined);
  document.addEventListener("pointerdown", onDocPointerDown);
});

onBeforeUnmount(() => document.removeEventListener("pointerdown", onDocPointerDown));

interface NavItem {
  to: string;
  label: string;
  icon: "home" | "interview" | "agent" | "diary" | "experience" | "knowledge" | "graph" | "search" | "settings";
}

const mainNav = computed<NavItem[]>(() => [
  { to: "/", label: "首页", icon: "home" },
]);

const assetNav = computed<NavItem[]>(() => [
  { to: "/diaries", label: "日记", icon: "diary" },
  { to: "/experiences", label: "经验", icon: "experience" },
  { to: "/knowledge", label: "知识库", icon: "knowledge" },
  { to: "/graph", label: "关系图谱", icon: "graph" },
  { to: "/search", label: "搜索", icon: "search" },
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

/** 编辑态下点整行不要跳转 */
function onRowClick(item: ConversationListItem, event: MouseEvent) {
  if (editingId.value === item.id) {
    event.preventDefault();
    return;
  }
  emit("navigate");
}

/**
 * 编辑态下禁止链接拖拽。输入框在 <a> 里面，拖动选中文字时浏览器会把整行当链接拖，
 * 松开后可能直接跳转或露出地址，看起来像「拖出网址」。
 */
function onDragStart(item: ConversationListItem, event: DragEvent) {
  if (editingId.value === item.id) {
    event.preventDefault();
  }
}

/** 菜单左边缘对齐「⋯」的左边缘；右侧只做屏幕兜底 */
function openMenu(item: ConversationListItem, event: MouseEvent) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const left = Math.max(0, Math.min(rect.left, window.innerWidth - MENU_WIDTH - 8));
  menuPos.value = { top: rect.bottom + 4, left };
  menuItem.value = item;
}

function closeMenu() {
  menuItem.value = null;
}

function onDocPointerDown(event: PointerEvent) {
  if (!menuItem.value) return;
  const target = event.target as HTMLElement | null;
  if (target?.closest(".thread-menu") || target?.closest(".thread-more")) return;
  closeMenu();
}

/** 重命名直接在标题位置变成输入框，不弹窗 */
async function startRename() {
  const item = menuItem.value;
  if (!item) return;
  closeMenu();
  editingId.value = item.id;
  editingTitle.value = item.title;
  skipBlur = false;
  await nextTick();
  const input = document.querySelector<HTMLInputElement>(`.thread-rename[data-rename="${item.id}"]`);
  input?.focus();
  input?.select();
}

async function commitRename() {
  if (skipBlur) {
    skipBlur = false;
    return;
  }
  const id = editingId.value;
  const title = editingTitle.value.trim();
  editingId.value = null;
  if (id == null || !title) return;
  // 原样失焦就不必发请求
  const current = conversations.sessions.find((item) => item.id === id);
  if (current && current.title === title) return;
  // 拖拽误拖进输入框会把链接地址写进标题，这种明显不是人写的标题直接拦掉
  if (/^https?:\/\//i.test(title)) {
    ui.toast("标题不能是链接地址", "warn");
    return;
  }
  try {
    await conversations.rename(id, title);
  } catch (error) {
    ui.toast(errorMessage(error, "重命名失败"), "error");
  }
}

function cancelRename() {
  skipBlur = true;
  editingId.value = null;
}

async function togglePin() {
  const item = menuItem.value;
  if (!item) return;
  closeMenu();
  try {
    await conversations.togglePin(item.id, !item.pinned);
  } catch (error) {
    ui.toast(errorMessage(error, "置顶失败"), "error");
  }
}

function askDelete() {
  if (!menuItem.value) return;
  deleteTarget.value = menuItem.value;
  closeMenu();
  deleteOpen.value = true;
}

async function confirmDelete() {
  const target = deleteTarget.value;
  if (!target) return;
  busy.value = true;
  try {
    await conversations.remove(target.id);
    deleteOpen.value = false;
    if (isThread(target.id)) await router.push("/");
  } catch (error) {
    ui.toast(errorMessage(error, "删除失败"), "error");
  } finally {
    busy.value = false;
  }
}
</script>
