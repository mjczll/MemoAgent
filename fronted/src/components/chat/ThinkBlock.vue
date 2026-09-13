<template>
  <details class="think-block" :open="defaultOpen">
    <summary class="think-head">
      <svg class="think-caret" viewBox="0 0 16 16" aria-hidden="true">
        <path d="M5 3l6 5-6 5" />
      </svg>
      <span class="think-title">{{ title }}</span>
      <span class="think-count">{{ steps.length }} 步</span>
    </summary>
    <div class="think-body">
      <div v-for="(step, index) in steps" :key="index" class="think-step">
        <span class="think-idx">{{ String(index + 1).padStart(2, "0") }}</span>
        <div>
          <p class="think-label">{{ step.label }}</p>
          <p class="think-detail">{{ step.detail }}</p>
        </div>
      </div>
    </div>
  </details>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    steps: Array<{ label: string; detail: string }>;
    defaultOpen?: boolean;
  }>(),
  { title: "思考过程", defaultOpen: false },
);
</script>