<template>
  <div class="step-track">
    <div class="row between" style="padding: 4px 10px 0">
      <b class="small">采访五步</b>
      <span class="small faint mono">{{ doneCount }}/{{ steps.length }}</span>
    </div>
    <div class="progress" style="margin: 8px 10px 10px">
      <i :style="{ width: `${percent}%` }" />
    </div>
    <div
      v-for="(step, index) in steps"
      :key="step.id"
      class="step"
      :class="{ active: index === currentIndex, done: index < currentIndex }"
    >
      <span class="idx">{{ index < currentIndex ? "✓" : index + 1 }}</span>
      <div>
        <b style="font-weight: 500">{{ step.label }}</b>
        <span class="hint">{{ step.hint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { InterviewStep } from "@/types";

const props = defineProps<{ steps: InterviewStep[]; currentIndex: number }>();

const doneCount = computed(() => Math.min(props.currentIndex, props.steps.length));
const percent = computed(() =>
  Math.round((doneCount.value / Math.max(props.steps.length, 1)) * 100),
);
</script>
