<template>
  <div
    class="bubble-row"
    :class="{ user: message.role === 'user' }"
    :data-msg-id="message.id"
  >
    <div v-if="message.role !== 'user'" class="avatar">{{ avatar }}</div>
    <div class="stack bubble-stack">
      <div v-if="showWho" class="who small">{{ whoLabel }}</div>
      <div class="bubble">
        <MarkdownView v-if="markdown && message.role !== 'user'" :text="message.text" />
        <template v-else>{{ message.text }}</template>
      </div>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownView from "@/components/base/MarkdownView.vue";

const props = withDefaults(
  defineProps<{
    message: { id?: string; role: "user" | "ai"; text: string };
    markdown?: boolean;
    showWho?: boolean;
    whoLabel?: string;
  }>(),
  { markdown: true, showWho: false },
);

const avatar = computed(() => (props.message.role === "user" ? "我" : "✦"));
const whoLabel = computed(
  () => props.whoLabel ?? (props.message.role === "user" ? "我" : "MemoAgent"),
);
</script>