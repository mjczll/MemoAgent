<template>
  <div class="filter-chips" role="group" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="filter-chip"
      :class="{ on: modelValue.includes(option.value) }"
      :aria-pressed="modelValue.includes(option.value)"
      @click="toggle(option.value)"
    >
      # {{ option.label }}
      <span v-if="option.count !== undefined" class="filter-count">{{ option.count }}</span>
    </button>
    <button
      v-if="clearable && modelValue.length"
      type="button"
      class="filter-chip clear"
      @click="emit('update:modelValue', [])"
    >
      清除标签
    </button>
  </div>
</template>

<script setup lang="ts">
import type { FilterOption } from "./filterOption";

/**
 * 标签集合筛选，支持多选：点选叠加、再点取消。
 * 多标签在查询端为 AND 语义（同时包含全部选中标签）。
 */
const props = withDefaults(
  defineProps<{
    /** 当前选中的标签值集合 */
    modelValue: string[];
    options: FilterOption[];
    clearable?: boolean;
    ariaLabel?: string;
  }>(),
  { clearable: true, ariaLabel: "按标签筛选" },
);

const emit = defineEmits<{ (e: "update:modelValue", value: string[]): void }>();

function toggle(value: string) {
  const next = props.modelValue.includes(value)
    ? props.modelValue.filter((item) => item !== value)
    : [...props.modelValue, value];
  emit("update:modelValue", next);
}
</script>
