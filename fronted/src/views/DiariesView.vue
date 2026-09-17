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
        <b>共 {{ total }} 篇</b>
        <span class="spacer" />
        <span class="small faint">{{ loading ? "正在从后端加载…" : "点击任意一条进入详情" }}</span>
      </div>
      <div v-if="error" class="panel-body">
        <EmptyState icon="⚠" title="日记加载失败" :hint="error">
          <button class="btn primary sm" @click="load">重试</button>
        </EmptyState>
      </div>
      <div v-else class="list">
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
            <span>经验 {{ item.experienceCount ?? item.experienceIds.length }} · 知识 {{ item.knowledgeCount ?? item.knowledgeIds.length }}</span>
          </div>
          <div class="desc">{{ item.summary }}</div>
        </div>
      </div>
      <EmptyState
        v-if="!loading && !error && !list.length"
        icon="📔"
        title="还没有匹配的日记"
        hint="换个关键词或类型筛选，也可以直接写一篇。"
      >
        <button class="btn primary sm" @click="router.push('/diaries/new')">写日记</button>
      </EmptyState>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import EmptyState from "@/components/base/EmptyState.vue";
import KindTabs from "@/components/base/KindTabs.vue";
import { relative } from "@/lib/format";
import type { Diary } from "@/types";
import { useLibraryStore } from "@/stores/library";

const router = useRouter();
const library = useLibraryStore();

const keyword = ref("");
const kind = ref("all");
const sort = ref<"desc" | "asc">("desc");
const list = ref<Diary[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");
let debounce: number | undefined;

const stats = computed(() => library.stats);

const kindOptions = computed(() => [
  {
    value: "all",
    label: "全部",
    count: library.diaryKinds.reduce((sum, item) => sum + item.diaryCount, 0) || library.diaries.length,
  },
  ...library.diaryKinds.map((item) => ({
    value: item.name,
    label: item.name,
    count: item.diaryCount,
  })),
]);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const page = await library.queryDiaries({
      keyword: keyword.value,
      kind: kind.value,
      sort: sort.value,
      page: 1,
      size: 100,
    });
    list.value = page.records;
    total.value = page.total;
  } catch (err) {
    list.value = [];
    total.value = 0;
    error.value = errorMessage(err, "日记列表加载失败");
  } finally {
    loading.value = false;
  }
}

function scheduleLoad() {
  window.clearTimeout(debounce);
  debounce = window.setTimeout(() => {
    void load();
  }, 280);
}

onMounted(async () => {
  await library.hydrate();
  void load();
});

watch(
  () => library.diaryKinds.map((item) => item.name).join(","),
  () => {
    if (kind.value !== "all" && !library.diaryKinds.some((item) => item.name === kind.value)) {
      kind.value = "all";
    }
  },
);

watch([kind, sort], () => {
  void load();
});

watch(keyword, () => {
  scheduleLoad();
});
</script>
