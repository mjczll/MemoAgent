<template>
  <div class="toasts">
    <div v-for="item in ui.toasts" :key="item.id" class="toast" :class="item.kind" @click="ui.dismiss(item.id)">
      <span>{{ ICONS[item.kind] }}</span>
      <span>{{ item.text }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();

const ICONS: Record<string, string> = {
  info: "•",
  ok: "✓",
  warn: "!",
  error: "✕",
};
</script>
