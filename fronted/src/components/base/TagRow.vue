<template>
  <div class="tags">
    <span v-for="tag in tags" :key="tag" class="tag link" @click="emit('pick', tag)"># {{ tag }}</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ tags: string[] }>(), { tags: () => [] });
const emit = defineEmits<{ (e: "pick", tag: string): void }>();
</script>
