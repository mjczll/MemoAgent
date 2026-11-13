<template>
  <div class="editor-segment" role="radiogroup" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="option"
      type="button"
      role="radio"
      :aria-checked="option === modelValue"
      :class="{ on: option === modelValue }"
      @click="emit('update:modelValue', option)"
    >
      <span class="dot" aria-hidden="true" />
      {{ option }}
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * 编辑控件（修改数据状态），不是筛选。
 * 视觉上必须与 FilterSegment 区分：带圆点指示、选中为描边而非填充，
 * 让用户一眼知道「点这里会改数据」。
 */
defineProps<{
  modelValue: string;
  options: string[];
  ariaLabel?: string;
}>();

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();
</script>
