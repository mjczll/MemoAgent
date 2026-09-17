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

function placeMenu() {
  const input = root.value?.querySelector("input");
  if (!input) return;
  const rect = input.getBoundingClientRect();
  menuStyle.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
  };
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
});

onUnmounted(() => {
  document.removeEventListener("pointerdown", onDocPointerDown);
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
  padding: 4px;
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
