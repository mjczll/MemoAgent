<template>
  <router-link class="entry-card" :to="to">
    <div class="row" style="gap: 8px">
      <span class="kind-dot" :style="{ background: color }" />
      <h3 class="grow" style="flex: 1">{{ title }}</h3>
      <span v-if="badge" class="badge">{{ badge }}</span>
    </div>

    <div class="row wrap small faint" style="gap: 12px">
      <span v-for="meta in metaItems" :key="meta">{{ meta }}</span>
    </div>

    <p>{{ desc }}</p>

    <div v-if="tags.length" class="tags">
      <span v-for="tag in tags.slice(0, 4)" :key="tag" class="tag"># {{ tag }}</span>
    </div>

    <slot />
  </router-link>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    desc: string;
    to: string;
    metaItems?: string[];
    tags?: string[];
    badge?: string;
    color?: string;
  }>(),
  {
    metaItems: () => [],
    tags: () => [],
    badge: "",
    color: "var(--accent)",
  },
);
</script>
