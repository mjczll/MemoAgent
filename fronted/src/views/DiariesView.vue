<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">日记</h1>
        <p class="lede" style="margin-bottom: 0">
          全部原始记录都在这里：{{ stats.interviewOrigin }} 篇来自采访，{{ stats.diaries - stats.interviewOrigin - (stats.reviewOrigin ?? 0) }} 篇手动记录{{ (stats.reviewOrigin ?? 0) ? `，${stats.reviewOrigin} 篇复盘` : "" }}。
        </p>
      </div>
      <button class="btn primary" @click="router.push('/diaries/new')">写日记</button>
    </div>

    <FilterBar>
      <FilterSearch
        v-model="filters.keyword"
        placeholder="搜索标题、摘要或正文…"
        @search="onFilterChange"
      />
      <FilterSelect
        v-model="filters.kind"
        :options="kindOptions"
        label="类型"
        aria-label="按类型筛选"
        @update:model-value="onFilterChange"
      />
      <FilterSegment
        v-model="filters.origin"
        :options="originOptions"
        aria-label="按来源筛选"
        @update:model-value="onFilterChange"
      />
      <FilterSegment
        v-model="filters.sort"
        :options="sortOptions"
        aria-label="排序方式"
        @update:model-value="onFilterChange"
      />
    </FilterBar>

    <div v-if="tagOptions.length" style="margin-bottom: 12px">
      <TagChips
        v-model="filters.tag"
        :options="tagOptions"
        @update:model-value="onFilterChange"
      />
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
            <span v-else-if="item.origin === 'review'" class="tag">复盘</span>
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
        hint="换个关键词或筛选条件，也可以直接写一篇。"
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
import { listDiaryTags, type TagCount } from "@/api/diary";
import EmptyState from "@/components/base/EmptyState.vue";
import FilterBar from "@/components/base/FilterBar.vue";
import FilterSearch from "@/components/base/FilterSearch.vue";
import FilterSegment from "@/components/base/FilterSegment.vue";
import FilterSelect from "@/components/base/FilterSelect.vue";
import TagChips from "@/components/base/TagChips.vue";
import { relative } from "@/lib/format";
import { useFilterSync } from "@/lib/filterSync";
import type { Diary } from "@/types";
import { useLibraryStore } from "@/stores/library";

const router = useRouter();
const library = useLibraryStore();

/** 筛选状态以 URL 为唯一事实源：UI 变化 → writeRoute → watch(route) → load */
const { filters, applyRoute, writeRoute } = useFilterSync({
  routeName: "diaries",
  defaults: {
    keyword: "",
    kind: "all",
    origin: "all",
    tag: "all",
    sort: "desc",
    page: "1",
  },
  onChange: () => load(),
});

const list = ref<Diary[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");

/** 后端返回的是 {name, count}，TagChips 需要的是 {value, label, count}，在这里做映射 */
const tagCounts = ref<TagCount[]>([]);
const tagOptions = computed(() =>
  tagCounts.value.map((tag) => ({
    value: tag.name,
    label: tag.name,
    count: tag.count,
  })),
);

const stats = computed(() => library.stats);

const kindOptions = computed(() => [
  {
    value: "all",
    label: "全部",
    count: library.diaryKinds.reduce((sum, item) => sum + item.diaryCount, 0) || library.diaries.length,
  },
  ...library.diaryKinds
    .filter((item) => item.diaryCount > 0)
    .map((item) => ({
      value: item.name,
      label: item.name,
      count: item.diaryCount,
    })),
]);

const originOptions = [
  { value: "all", label: "全部来源" },
  { value: "manual", label: "用户编写" },
  { value: "interview", label: "采访生成" },
  { value: "review", label: "复盘" },
];

const sortOptions = [
  { value: "desc", label: "最新优先" },
  { value: "asc", label: "最早优先" },
];

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const page = await library.queryDiaries({
      keyword: filters.keyword,
      kind: filters.kind,
      origin: filters.origin,
      tag: filters.tag,
      sort: filters.sort === "asc" ? "asc" : "desc",
      page: Number(filters.page) || 1,
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

/** 任何筛选变化：page 归 1，写进 URL；加载由 route watcher 统一触发 */
function onFilterChange() {
  filters.page = "1";
  writeRoute();
}

async function loadTags() {
  try {
    tagCounts.value = await listDiaryTags();
  } catch {
    tagCounts.value = [];
  }
}

onMounted(async () => {
  applyRoute();
  void loadTags();
  await library.hydrate();
  await load();
});

watch(
  () => library.diaryKinds.map((item) => item.name).join(","),
  () => {
    if (filters.kind !== "all" && !library.diaryKinds.some((item) => item.name === filters.kind)) {
      filters.kind = "all";
      writeRoute();
    }
  },
);
</script>
