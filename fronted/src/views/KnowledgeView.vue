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
      <button class="btn primary" @click="openCreate">新建知识</button>
    </div>

    <div class="toolbar" style="margin-bottom: 14px">
      <div class="search grow" style="min-width: 200px">
        <input v-model="keyword" class="input" placeholder="搜索标题、摘要或正文…" />
      </div>
      <KindTabs v-model="domain" :options="domainOptions" />
      <span class="spacer" />
      <div class="seg">
        <button :class="{ on: mastery === 'all' }" @click="mastery = 'all'">全部掌握度</button>
        <button
          v-for="m in MASTERIES"
          :key="m"
          :class="{ on: mastery === m }"
          @click="mastery = m"
        >
          {{ m }}
        </button>
      </div>
    </div>

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
      <button class="btn primary sm" @click="openCreate">新建知识</button>
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
            <select v-model="createForm.domain" class="select">
              <option v-for="d in DOMAIN_NAMES" :key="d" :value="d">{{ d }}</option>
            </select>
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
          <button class="btn primary" :disabled="!createForm.title.trim()" @click="create">保存</button>
        </div>
      </template>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import KindTabs from "@/components/base/KindTabs.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import { DOMAIN_NAMES } from "@/mock";
import { relative } from "@/lib/format";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { Mastery } from "@/types";

const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const MASTERIES: Mastery[] = ["已掌握", "部分掌握", "待补充"];

const keyword = ref("");
const domain = ref("all");
const mastery = ref<"all" | Mastery>("all");
const showCreate = ref(false);

const createForm = reactive({
  title: "",
  domain: DOMAIN_NAMES[0] ?? "Java",
  category: "",
  tagsText: "",
  content: "",
});

const stats = computed(() => library.stats);

const domainOptions = computed(() => [
  { value: "all", label: "全部领域", count: library.knowledge.length },
  ...DOMAIN_NAMES.filter((d) => library.knowledge.some((k) => k.domain === d)).map((d) => ({
    value: d,
    label: d,
    count: library.knowledge.filter((k) => k.domain === d).length,
  })),
]);

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  return library.knowledge
    .filter((k) => (domain.value === "all" ? true : k.domain === domain.value))
    .filter((k) => (mastery.value === "all" ? true : k.mastery === mastery.value))
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
  createForm.category = "";
  createForm.tagsText = "";
  createForm.content = "";
  showCreate.value = true;
}

function create() {
  const tags = createForm.tagsText
    .split(/[,，、\s]+/)
    .map((t) => t.trim())
    .filter(Boolean);
  const id = library.createKnowledge({
    title: createForm.title.trim(),
    domain: createForm.domain,
    category: createForm.category.trim() || "未分类",
    tags,
    content: createForm.content.split("\n").slice(0, 1).join("") || "",
    summary: createForm.content.trim().slice(0, 60),
  });
  showCreate.value = false;
  ui.toast("知识条目已创建");
  router.push(`/knowledge/${id}`);
}
</script>
