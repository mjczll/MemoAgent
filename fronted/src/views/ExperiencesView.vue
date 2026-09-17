<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">经验</h1>
        <p class="lede" style="margin-bottom: 0">
          每张经验卡都是「问题 → 原因 → 解法 → 经验」的结构化结论，来自你自己的日记。
        </p>
      </div>
      <button class="btn" @click="router.push('/graph')">在关系图谱中查看</button>
    </div>

    <div class="toolbar" style="margin-bottom: 14px">
      <div class="search grow" style="min-width: 200px">
        <input v-model="keyword" class="input" placeholder="搜索问题、解法或经验…" />
      </div>
      <KindTabs v-model="domain" :options="domainOptions" />
    </div>

    <div class="grid cols-3">
      <EntryCard
        v-for="item in list"
        :key="item.id"
        :title="item.title"
        :desc="`问题：${item.problem}\n经验：${item.lesson}`"
        :to="`/experiences/${item.id}`"
        :badge="item.domain"
        :meta-items="[item.createdAt.slice(0, 10), `关联知识 ${item.knowledgeIds.length}`]"
        :tags="item.tags"
        color="#e8c07d"
      />
    </div>

    <EmptyState
      v-if="!list.length"
      icon="🧩"
      title="还没有匹配的经验卡"
      hint="经验来自日记的自动提炼，先去采访或写一篇日记吧。"
    >
      <button class="btn primary sm" @click="router.push('/interview')">开始采访</button>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import EntryCard from "@/components/base/EntryCard.vue";
import KindTabs from "@/components/base/KindTabs.vue";
import { useLibraryStore } from "@/stores/library";

const router = useRouter();
const library = useLibraryStore();

const keyword = ref("");
const domain = ref("all");

const domainOptions = computed(() => {
  const counts = library.domainCounts;
  const names = Object.keys(counts).sort((a, b) => (counts[b] ?? 0) - (counts[a] ?? 0));
  return [
    { value: "all", label: "全部领域", count: library.experiences.length },
    ...names
      .filter((n) => library.experiences.some((e) => e.domain === n))
      .map((n) => ({ value: n, label: n, count: library.experiences.filter((e) => e.domain === n).length })),
  ];
});

const list = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  return library.experiences
    .filter((e) => (domain.value === "all" ? true : e.domain === domain.value))
    .filter((e) =>
      !q
        ? true
        : [e.title, e.problem, e.cause, e.solution, e.lesson, e.tags.join(" ")]
            .join(" ")
            .toLowerCase()
            .includes(q),
    );
});
</script>
