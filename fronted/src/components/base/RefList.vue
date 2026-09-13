<template>
  <div v-if="rows.length" class="stack" style="gap: 8px">
    <router-link
      v-for="row in rows"
      :key="`${row.kind}-${row.id}`"
      class="list-row"
      :to="row.to"
    >
      <div class="title">
        <span class="kind-dot" :style="{ background: row.color }" />
        <b>{{ row.title }}</b>
        <span class="badge">{{ row.kindLabel }}</span>
      </div>
      <div class="meta">
        <span>{{ row.reason }}</span>
        <span class="grow" />
        <span class="faint">查看 →</span>
      </div>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { AgentRef } from "@/types";
import { useLibraryStore } from "@/stores/library";

const props = withDefaults(defineProps<{ items: AgentRef[]; hideUnknown?: boolean }>(), {
  hideUnknown: true,
});

const library = useLibraryStore();

const COLOR: Record<string, string> = {
  diary: "#7f9db3",
  experience: "#e8c07d",
  knowledge: "#7fd1a8",
};

const LABEL: Record<string, string> = {
  diary: "日记",
  experience: "经验",
  knowledge: "知识",
};

const rows = computed(() =>
  props.items
    .map((item) => {
      const entity = library.byKind(item.id);
      const title = entity?.title ?? item.title;
      if (!entity && props.hideUnknown) return null;
      const to =
        entity?.kind === "diary"
          ? `/diaries/${item.id}`
          : entity?.kind === "experience"
            ? `/experiences/${item.id}`
            : `/knowledge/${item.id}`;
      return {
        id: item.id,
        kind: entity?.kind ?? item.kind,
        kindLabel: LABEL[entity?.kind ?? item.kind] ?? "关联",
        color: COLOR[entity?.kind ?? item.kind] ?? "var(--accent)",
        title,
        reason: item.reason,
        to,
      };
    })
    .filter((row): row is NonNullable<typeof row> => row !== null),
);
</script>
