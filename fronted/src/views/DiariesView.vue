<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">日记</h1>
        <p class="lede" style="margin-bottom: 0">
          全部原始记录都在这里：{{ stats.interviewOrigin }} 篇来自采访，{{ stats.diaries - stats.interviewOrigin }} 篇手动记录。
        </p>
      </div>
      <button class="btn primary" @click="router.push('/diaries/new')">写日记</button>
    </div>

    <div class="toolbar" style="margin-bottom: 10px">
      <div class="search grow" style="min-width: 220px">
        <input v-model="keyword" class="input" placeholder="搜索标题、摘要或正文…" />
      </div>
      <KindTabs v-model="kind" :options="kindOptions" />
      <span class="spacer" />
      <div class="seg">
        <button :class="{ on: sort === 'desc' }" @click="sort = 'desc'">最新优先</button>
        <button :class="{ on: sort === 'asc' }" @click="sort = 'asc'">最早优先</button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <b>共 {{ list.length }} 篇</b>
        <span class="spacer" />
        <span class="small faint">点击任意一条进入详情</span>
      </div>
      <div class="list">
        <div
          v-for="item in list"
          :key="item.id"
          class="list-row"
          @click="router.push(`/diaries/${item.id}`)"
        >
          <div class="title">
            <b>{{ item.title }}</b>
            <span class="badge">{{ item.kind }}</span>
            <span v-if="item.origin === 'interview'" class="tag accent">采访生成</span>
          </div>
          <div class="meta">
            <span>{{ item.date }}</span>
            <span>{{ relative(item.createdAt) }}</span>
            <span>经验 {{ item.experienceIds.length }} · 知识 {{ item.knowledgeIds.length }}</span>
          </div>
          <div class="desc">{{ item.summary }}</div>
        </div>
      </div>
      <EmptyState
        v-if="!list.length"
        icon="📔"
        title="还没有匹配的日记"
        hint="换个关键词或类型筛选，也可以直接让 AI 采访你一次。"
      >
        <button class="btn primary sm" @click="router.push('/interview')">开始采访</button>
      </EmptyState>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import KindTabs from "@/components/base/KindTabs.vue";
import { relative } from "@/lib/format";
import { DIARY_KINDS } from "@/types";
import { useLibraryStore } from "@/stores/library";

const router = useRouter();
const library = useLibraryStore();

const keyword = ref("");
const kind = ref("all");
const sort = ref<"desc" | "asc">("desc");

const KINDS = DIARY_KINDS;

const stats = computed(() => library.stats);

const kindOptions = computed(() => [
  { value: "all", label: "全部", count: library.diaries.length },
  ...KINDS.map((k) => ({
    value: k,
    label: k,
    count: library.diaries.filter((d) => d.kind === k).length,
  })),
]);

const list = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  return library.diaries
    .filter((d) => (kind.value === "all" ? true : d.kind === kind.value))
    .filter((d) =>
      !q
        ? true
        : d.title.toLowerCase().includes(q) ||
          d.summary.toLowerCase().includes(q) ||
          d.content.toLowerCase().includes(q) ||
          d.tags.join(" ").toLowerCase().includes(q),
    )
    .sort((a, b) => (sort.value === "desc" ? (a.date < b.date ? 1 : -1) : a.date < b.date ? -1 : 1));
});
</script>
