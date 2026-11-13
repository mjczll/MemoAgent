<template>
  <div class="filter-search">
    <input
      class="input"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      :aria-label="placeholder"
      @input="onInput"
    />
  </div>
</template>

<script setup lang="ts">
import { watch } from "vue";

const props = withDefaults(
  defineProps<{
    modelValue: string;
    placeholder?: string;
    /** 输入防抖毫秒数 */
    debounce?: number;
  }>(),
  { placeholder: "搜索…", debounce: 280 },
);

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "search", value: string): void;
}>();

let timer: number | undefined;
/** 最近一次本地输入的值，用于区分「用户在打字」和「外部重置了关键词」 */
let lastLocal: string | null = null;

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value;
  lastLocal = value;
  emit("update:modelValue", value);
  window.clearTimeout(timer);
  timer = window.setTimeout(() => emit("search", value), props.debounce);
}

// 外部把关键词改掉（例如 URL 同步、清空筛选）时，取消还没触发的防抖搜索
watch(
  () => props.modelValue,
  (value) => {
    if (lastLocal !== null && value !== lastLocal) {
      window.clearTimeout(timer);
      lastLocal = null;
    }
  },
);
</script>
