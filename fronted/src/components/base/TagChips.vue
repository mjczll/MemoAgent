<template>
  <div class="filter-chips" role="group" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="filter-chip"
      :class="{ on: option.value === modelValue }"
      :aria-pressed="option.value === modelValue"
      @click="emit('update:modelValue', option.value === modelValue ? allValue : option.value)"
    >
      # {{ option.label }}
      <span v-if="option.count !== undefined" class="filter-count">{{ option.count }}</span>
    </button>
    <button
      v-if="clearable && modelValue !== allValue"
      type="button"
      class="filter-chip clear"
      @click="emit('update:modelValue', allValue)"
    >
      清除标签
    </button>
  </div>
</template>

<script setup lang="ts">
import type { FilterOption } from "./filterOption";

/**
 * 标签集合筛选。与普通枚举筛选的区别：表达的是「标签集合」，
 * 视觉上比 Segment 低半档（30px vs 34px），单选起步。
 * 标签多时随容器换行；需要收纳时按规范升级为 FilterSelect。
 */
withDefaults(
  defineProps<{
    modelValue: string;
    options: FilterOption[];
    /** 代表「不限」的值 */
    allValue?: string;
    clearable?: boolean;
    ariaLabel?: string;
  }>(),
  { allValue: "all", clearable: true, ariaLabel: "按标签筛选" },
);

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();
</script>
