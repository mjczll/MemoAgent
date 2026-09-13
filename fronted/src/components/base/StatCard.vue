<template>
  <div class="stat" :class="{ linkable }" @click="linkable && emit('click')">
    <b>{{ value }}</b>
    <span>{{ label }}</span>
    <p v-if="hint" class="small faint" style="margin: 4px 0 0">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ label: string; value: string | number; hint?: string; linkable?: boolean }>(),
  { hint: "", linkable: false },
);

const emit = defineEmits<{ (e: "click"): void }>();
</script>

<style scoped>
.stat.linkable {
  cursor: pointer;
  transition: border-color 0.16s, transform 0.16s;
}
.stat.linkable:hover {
  border-color: var(--accent-line);
  transform: translateY(-2px);
}
</style>
