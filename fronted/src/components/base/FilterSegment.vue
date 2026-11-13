<template>
  <div class="filter-segment filter-control" role="group" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="{ on: option.value === modelValue }"
      :aria-pressed="option.value === modelValue"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
      <span v-if="option.count !== undefined" class="filter-count">{{ option.count }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { FilterOption } from "./filterOption";

/** 少量、固定、互斥的筛选选项（≤4 个）。选项会增长的维度请用 FilterSelect。 */
defineProps<{
  modelValue: string;
  options: FilterOption[];
  ariaLabel?: string;
}>();

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();
</script>
