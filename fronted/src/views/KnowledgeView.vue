<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">知识库</h1>
        <p class="lede" style="margin-bottom: 0">
          共 {{ library.knowledge.length }} 条知识，已掌握 {{ stats.mastered }} 条（{{ stats.masteryRate }}%），
          涵盖 {{ stats.domains }} 个领域。
        </p>
      </div>
      <button type="button" class="btn primary" @click="openCreate">新建知识</button>
    </div>

    <FilterBar>
      <FilterSearch
        v-model="filters.keyword"
        placeholder="搜索标题、摘要或正文…"
        @search="writeRoute"
      />
      <FilterSelect
        v-model="filters.domain"
        :options="domainOptions"
        label="领域"
        aria-label="按领域筛选"
        @update:model-value="writeRoute"
      />
      <FilterSegment
        v-model="filters.mastery"
        :options="masteryOptions"
        aria-label="按掌握度筛选"
        @update:model-value="writeRoute"
      />
    </FilterBar>

    <div class="stack">
      <div v-for="group in groups" :key="group.category" class="panel">
        <div class="panel-head">
          <b>{{ group.category }}</b>
          <span class="spacer" />
          <span class="small faint">{{ group.items.length }} 条</span>
        </div>
        <div class="list">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="list-row"
            @click="router.push(`/knowledge/${item.id}`)"
          >
            <div class="title">
              <b>{{ item.title }}</b>
              <span class="tag" :class="{ ok: item.mastery === '已掌握', warn: item.mastery === '待补充' }">
                {{ item.mastery }}
              </span>
            </div>
            <div class="meta">
              <span>{{ item.domain }}</span>
              <span>{{ item.category }}</span>
              <span>互链 {{ item.relatedIds.length }}</span>
              <span>{{ relative(item.updatedAt) }}更新</span>
            </div>
            <div class="desc">{{ item.summary }}</div>
          </div>
        </div>
      </div>
    </div>

    <EmptyState
      v-if="!groups.length"
      icon="📚"
      title="没有匹配的知识条目"
      hint="换个关键词或领域试试，也可以手动新建一条。"
    >
      <button type="button" class="btn primary sm" @click="openCreate">新建知识</button>
    </EmptyState>

    <ModalDialog v-model="showCreate" title="新建知识条目" width="680px">
      <div class="stack">
        <div class="field">
          <label>标题</label>
          <input v-model="createForm.title" class="input" placeholder="例如：Redis 缓存击穿的三种成因" />
        </div>
        <div class="row wrap">
          <div class="field grow" style="min-width: 180px">
            <label>领域</label>
            <SuggestCombo
              v-model="createForm.domain"
              :options="knownDomains"
              :maxlength="64"
              placeholder="选择已有领域，或输入新领域"
              aria-label="选择已有领域"
            />
          </div>
          <div class="field grow" style="min-width: 180px">
            <label>分类</label>
            <input v-model="createForm.category" class="input" placeholder="例如：缓存" />
          </div>
        </div>
        <div class="field">
          <label>标签（逗号分隔）</label>
          <input v-model="createForm.tagsText" class="input" placeholder="Redis, 缓存, 一致性" />
        </div>
        <div class="field">
          <label>正文（Markdown）</label>
          <textarea v-model="createForm.content" class="textarea" style="min-height: 160px" />
        </div>
      </div>
      <template #foot>
        <div class="row" style="justify-content: flex-end; width: 100%">
          <button class="btn" @click="showCreate = false">取消</button>
          <button class="btn primary" :disabled="!canCreate" @click="create">保存</button>
        </div>
      </template>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import EmptyState from "@/components/base/EmptyState.vue";
import FilterBar from "@/components/base/FilterBar.vue";
import FilterSearch from "@/components/base/FilterSearch.vue";
import FilterSegment from "@/components/base/FilterSegment.vue";
import FilterSelect from "@/components/base/FilterSelect.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import SuggestCombo from "@/components/base/SuggestCombo.vue";
import { collectDomains, normalizeDomain } from "@/lib/domain";
import { relative } from "@/lib/format";
import { useFilterSync } from "@/lib/filterSync";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { Mastery } from "@/types";

const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const MASTERIES: Mastery[] = ["已掌握", "部分掌握", "待补充"];

/** 领域无限增长用下拉，掌握度是固定三个枚举用胶囊（筛选语言规范） */
const { filters, applyRoute, writeRoute } = useFilterSync({
  routeName: "knowledge",
  defaults: {
    keyword: "",
    domain: "all",
    mastery: "all",
  },
});

// 首次进入时从 URL 恢复筛选状态（客户端过滤是响应式的，无需手动触发查询）
applyRoute();

const showCreate = ref(false);

const createForm = reactive({
  title: "",
  domain: "",
  category: "",
  tagsText: "",
  content: "",
});

const stats = computed(() => library.stats);
const knownDomains = computed(() => collectDomains(library.knowledge));
const canCreate = computed(() => Boolean(createForm.title.trim() && createForm.domain.trim()));

const masteryOptions = [
  { value: "all", label: "全部掌握度" },
  ...MASTERIES.map((m) => ({ value: m, label: m })),
];

const domainOptions = computed(() => {
  const counts = new Map<string, number>();
  for (const item of library.knowledge) {
    const name = item.domain.trim();
    if (!name) continue;
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  return [
    { value: "all", label: "全部领域", count: library.knowledge.length },
    ...knownDomains.value.map((name) => ({
      value: name,
      label: name,
      count: counts.get(name) ?? 0,
    })),
  ];
});

const filtered = computed(() => {
  const q = filters.keyword.trim().toLowerCase();
  return library.knowledge
    .filter((k) => (filters.domain === "all" ? true : k.domain === filters.domain))
    .filter((k) => (filters.mastery === "all" ? true : k.mastery === filters.mastery))
    .filter((k) =>
      !q
        ? true
        : [k.title, k.summary, k.content, k.tags.join(" "), k.category]
            .join(" ")
            .toLowerCase()
            .includes(q),
    );
});

const groups = computed(() => {
  const map = new Map<string, typeof library.knowledge>();
  filtered.value.forEach((k) => {
    const key = k.category || "未分类";
    const list = map.get(key) ?? [];
    list.push(k);
    map.set(key, list);
  });
  return [...map.entries()].map(([category, items]) => ({ category, items }));
});

function openCreate() {
  createForm.title = "";
  createForm.domain = "";
  createForm.category = "";
  createForm.tagsText = "";
  createForm.content = "";
  showCreate.value = true;
}

watch(knownDomains, (names) => {
  if (filters.domain !== "all" && !names.includes(filters.domain)) {
    filters.domain = "all";
    writeRoute();
  }
});

async function create() {
  if (!canCreate.value) return;
  const tags = createForm.tagsText
    .split(/[,，、\s]+/)
    .map((t) => t.trim())
    .filter(Boolean);
  const content = createForm.content.trim() || createForm.title.trim();
  try {
    const id = await library.createKnowledge({
      title: createForm.title.trim(),
      domain: normalizeDomain(createForm.domain),
      category: createForm.category.trim() || "未分类",
      tags,
      content,
      summary: content.slice(0, 60),
    });
    showCreate.value = false;
    ui.toast("知识条目已创建");
    router.push(`/knowledge/${id}`);
  } catch (error) {
    ui.toast(errorMessage(error, "创建失败"), "error");
  }
}
</script>
