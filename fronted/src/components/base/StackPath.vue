<template>
  <div class="stack-path">
    <template v-for="(node, index) in nodes" :key="`${node.kind}-${node.id}-${index}`">
      <span
        class="node"
        :class="`kind-${node.kind}`"
        @click="open(node)"
      >
        {{ node.title }}
      </span>
      <span v-if="index < nodes.length - 1" class="arrow">→</span>
    </template>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

defineProps<{
  nodes: Array<{ id: string; kind: "diary" | "experience" | "knowledge"; title: string }>;
}>();

const router = useRouter();

function open(node: { id: string; kind: string }) {
  if (node.kind === "diary") router.push(`/diaries/${node.id}`);
  else if (node.kind === "experience") router.push(`/experiences/${node.id}`);
  else router.push(`/knowledge/${node.id}`);
}
</script>