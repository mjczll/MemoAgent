<template>
  <div class="chip-row" :style="{ justifyContent: justify }">
    <button v-for="item in items" :key="item" class="chip" @click="emit('pick', item)">
      {{ item }}
    </button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ items: string[]; justify?: string }>(), { justify: "flex-start" });
const emit = defineEmits<{ (e: "pick", value: string): void }>();
</script>
