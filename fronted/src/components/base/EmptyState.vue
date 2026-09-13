<template>
  <div class="empty">
    <span class="big">{{ icon }}</span>
    <b>{{ title }}</b>
    <p class="small" style="margin: 6px 0 0">{{ hint }}</p>
    <div v-if="$slots.default" style="margin-top: 12px">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; hint?: string; icon?: string }>(), {
  hint: "换个筛选条件，或者先去采访一次把素材补上。",
  icon: "🧊",
});
</script>
