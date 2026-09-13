<template>
  <div class="compare-row">
    <div class="compare-col" :class="{ 'is-best': leftBest }">
      <h4>
        {{ left.title }}
        <span v-if="leftBest" class="mark">优选</span>
      </h4>
      <ul>
        <li v-for="(item, idx) in left.items" :key="`l-${idx}`">{{ item }}</li>
      </ul>
    </div>
    <div class="compare-col" :class="{ 'is-best': rightBest }">
      <h4>
        {{ right.title }}
        <span v-if="rightBest" class="mark">优选</span>
      </h4>
      <ul>
        <li v-for="(item, idx) in right.items" :key="`r-${idx}`">{{ item }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  left: { title: string; items: string[] };
  right: { title: string; items: string[] };
  best?: "left" | "right";
}>();

const leftBest = computed(() => props.best === "left");
const rightBest = computed(() => props.best === "right");
</script>