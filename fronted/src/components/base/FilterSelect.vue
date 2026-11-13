<template>
  <label class="filter-field filter-control">
    <span v-if="label" class="filter-label">{{ label }}</span>
    <select
      class="filter-select"
      :value="modelValue"
      :aria-label="ariaLabel ?? label"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}{{ option.count !== undefined ? `（${option.count}）` : "" }}
      </option>
    </select>
  </label>
</template>

<script setup lang="ts">
import type { FilterOption } from "./filterOption";

/**
 * 选项可能继续增长的筛选维度（类型、领域……）。
 * 原生 select 起步，等真需要搜索/多选再升级为自定义下拉。
 */
withDefaults(
  defineProps<{
    modelValue: string;
    options: FilterOption[];
    /** 显示在下拉框前面的语义标签，例如「类型」「领域」 */
    label?: string;
    ariaLabel?: string;
  }>(),
  { label: "", ariaLabel: "" },
);

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();
</script>
