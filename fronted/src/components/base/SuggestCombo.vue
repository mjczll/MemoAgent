<template>
  <div ref="root" class="suggest-combo" :class="{ open }">
    <input
      class="input"
      :value="modelValue"
      :maxlength="maxlength"
      :placeholder="placeholder"
      @input="onInput"
      @focus="openMenu"
    />
    <button
      v-if="options.length"
      type="button"
      class="suggest-caret"
      :aria-expanded="open"
      :aria-label="ariaLabel"
      @click="toggle"
    >
      ▾
    </button>
    <Teleport to="body">
      <div v-if="open && options.length" ref="menu" class="suggest-menu" :style="menuStyle">
        <button
          v-for="option in options"
          :key="option"
          type="button"
          class="suggest-option"
          :class="{ on: modelValue === option }"
          @click="pick(option)"
        >
          {{ option }}
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from "vue";

/** 菜单最高 280px，选项再多就在菜单内部滚动 */
const MENU_MAX_HEIGHT = 280;
/** 视口剩余空间再小也保留 120px，否则只能显示一两项 */
const MENU_MIN_HEIGHT = 120;

const props = withDefaults(
  defineProps<{
    modelValue: string;
    options: string[];
    placeholder?: string;
    maxlength?: number;
    ariaLabel?: string;
  }>(),
  {
    placeholder: "选择已有名称，或输入新的",
    maxlength: 64,
    ariaLabel: "选择已有选项",
  },
);

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "touch"): void;
}>();

const open = ref(false);
const root = ref<HTMLElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const menuStyle = ref<Record<string, string>>({});
let frame = 0;

function placeMenu() {
  const input = root.value?.querySelector("input");
  if (!input) return;
  const rect = input.getBoundingClientRect();
  // 输入框滚出视口后，fixed 定位的菜单会孤零零悬在屏幕上，直接收起来更符合预期
  if (rect.bottom < 0 || rect.top > window.innerHeight) {
    open.value = false;
    return;
  }
  const gap = 4;
  const edge = 12;
  const below = window.innerHeight - rect.bottom - edge;
  const above = rect.top - edge;
  // 限高后菜单内部滚动，避免选项多时把屏幕撑满
  const maxHeight = Math.min(MENU_MAX_HEIGHT, Math.max(MENU_MIN_HEIGHT, Math.max(below, above)));
  const style: Record<string, string> = {
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    maxHeight: `${maxHeight}px`,
  };
  // 下方放不下、上方更宽裕时翻到输入框上面展开
  if (below < 160 && above > below) {
    style.bottom = `${window.innerHeight - rect.top + gap}px`;
  } else {
    style.top = `${rect.bottom + gap}px`;
  }
  menuStyle.value = style;
}

/**
 * 菜单是 fixed 定位，位置只在打开时算过一次，所以页面滚动或窗口尺寸变化时必须重算，
 * 否则菜单会停在原地不跟着输入框走。用 rAF 节流，避免滚动时每帧都读布局。
 */
function onViewportChange(event?: Event) {
  if (!open.value || frame) return;
  // 菜单自己在滚动时不用重算：位置只取决于输入框，重算反而会让列表抖
  const target = event?.target;
  if (target instanceof Node && menu.value?.contains(target)) return;
  frame = window.requestAnimationFrame(() => {
    frame = 0;
    if (open.value) placeMenu();
  });
}

function onInput(event: Event) {
  emit("update:modelValue", (event.target as HTMLInputElement).value);
  emit("touch");
  openMenu();
}

function openMenu() {
  if (!props.options.length) return;
  open.value = true;
  void nextTick(placeMenu);
}

function toggle() {
  if (!props.options.length) {
    open.value = false;
    return;
  }
  open.value = !open.value;
  if (open.value) void nextTick(placeMenu);
}

function pick(name: string) {
  emit("update:modelValue", props.modelValue === name ? "" : name);
  emit("touch");
  open.value = false;
}

function onDocPointerDown(event: PointerEvent) {
  const target = event.target as Node;
  if (root.value?.contains(target) || menu.value?.contains(target)) return;
  open.value = false;
}

onMounted(() => {
  document.addEventListener("pointerdown", onDocPointerDown);
  // capture 才能收到内部滚动容器（例如主内容区）的 scroll，不只是 window
  window.addEventListener("scroll", onViewportChange, true);
  window.addEventListener("resize", onViewportChange);
});

onUnmounted(() => {
  document.removeEventListener("pointerdown", onDocPointerDown);
  window.removeEventListener("scroll", onViewportChange, true);
  window.removeEventListener("resize", onViewportChange);
  if (frame) {
    window.cancelAnimationFrame(frame);
    frame = 0;
  }
});
</script>

<style scoped>
.suggest-combo {
  position: relative;
}

.suggest-combo .input {
  padding-right: 32px;
}

.suggest-caret {
  position: absolute;
  top: 0;
  right: 0;
  width: 32px;
  height: 100%;
  border: 0;
  background: transparent;
  color: var(--mute);
  cursor: pointer;
}

.suggest-combo.open .suggest-caret {
  color: var(--ink);
}

.suggest-menu {
  position: fixed;
  z-index: 90;
  box-sizing: border-box;
  padding: 4px;
  /* 选项多时内部滚动，不把整屏撑满；overscroll-behavior 防止滚到边界后带动页面 */
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--line-2);
  border-radius: 5px;
  background: var(--paper-2);
  box-shadow: var(--shadow-sm);
}

.suggest-option {
  display: block;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.suggest-option:hover,
.suggest-option.on {
  background: var(--hover);
}
</style>
