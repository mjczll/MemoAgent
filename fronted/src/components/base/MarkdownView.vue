<template>
  <div class="md" v-html="html" @click="onClick" />
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { renderMarkdown } from "@/lib/markdown";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const props = defineProps<{ text: string }>();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const html = computed(() => renderMarkdown(props.text ?? ""));

/** 双链点击：命中已有知识则跳转，否则提示去沉淀 */
function onClick(event: MouseEvent) {
  const target = (event.target as HTMLElement).closest("a.wikilink") as HTMLElement | null;
  if (!target) return;
  const title = target.dataset.wikilink ?? target.textContent ?? "";
  const knowledge = library.knowledgeByTitle(title);
  if (knowledge) {
    router.push(`/knowledge/${knowledge.id}`);
    return;
  }
  ui.toast(`知识库里还没有「${title}」，可以在采访后沉淀它`, "warn");
  router.push({ path: "/search", query: { q: title } });
}
</script>
