<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">搜索</h1>
        <p class="lede" style="margin-bottom: 0">
          一次搜索，同时翻你的日记、经验、知识条目与探索卡片。
        </p>
      </div>
    </div>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="search grow" style="min-width: 240px">
        <input
          v-model="keyword"
          class="input"
          placeholder="试试：缓存击穿 / Spring AI / 索引失效"
          @keyup.enter="remember"
        />
      </div>
      <button class="btn primary" @click="remember">搜索</button>
      <button v-if="keyword" class="btn ghost" @click="clear">清空</button>
    </div>

    <div v-if="!keyword.trim()" class="panel">
      <div class="panel-head"><b>最近搜过</b></div>
      <div class="panel-body stack" style="gap: 12px">
        <PromptChips v-if="history.length" :items="history" @pick="applyKeyword" />
        <p v-else class="small faint" style="margin: 0">还没有搜索记录，试试下面的示例词。</p>
        <PromptChips :items="EXAMPLES" @pick="applyKeyword" />
      </div>
    </div>

    <template v-else>
      <div class="small faint" style="margin-bottom: 10px">
        「{{ keyword.trim() }}」共命中 {{ total }} 条
      </div>

      <div class="panel">
        <div v-for="group in groups" :key="group.kind" class="panel-body" style="padding: 0">
          <div class="panel-head" style="background: transparent">
            <b>{{ group.label }}</b>
            <span class="spacer" />
            <span class="small faint">{{ group.rows.length }} 条</span>
          </div>
          <div class="list">
            <div
              v-for="row in group.rows"
              :key="row.id"
              class="list-row"
              style="cursor: pointer"
              @click="router.push(row.to)"
            >
              <div class="title">
                <span class="kind-dot" :style="{ background: group.color }" />
                <b>{{ row.title }}</b>
              </div>
              <div class="meta">
                <span>{{ row.reason }}</span>
              </div>
            </div>
            <p v-if="!group.rows.length" class="small faint" style="padding: 10px 12px; margin: 0">
              没有命中的{{ group.label }}。
            </p>
          </div>
        </div>

        <EmptyState
          v-if="!total"
          icon="🔍"
          title="什么都没找到"
          hint="换一个更具体的技术词试试，比如「分布式锁」「Tool Calling」。"
        />
      </div>

      <div class="panel" style="margin-top: 12px">
        <div class="panel-head"><b>换个入口</b></div>
        <div class="panel-body small stack" style="gap: 8px">
          <button class="btn sm block" @click="askAgent">让 Agent 直接回答这个问题</button>
          <button class="btn sm block" @click="router.push('/graph')">去关系图谱看关联</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import PromptChips from "@/components/base/PromptChips.vue";
import { useAgentStore } from "@/stores/agent";
import { useLibraryStore } from "@/stores/library";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const agent = useAgentStore();

const HISTORY_KEY = "memoagent:search:v1";
const EXAMPLES = ["缓存击穿", "Spring AI", "索引失效", "Tool Calling", "线程池 OOM", "双链笔记"];

const keyword = ref(String(route.query.q ?? ""));

function loadHistory(): string[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

const history = ref<string[]>(loadHistory());

watch(
  () => route.query.q,
  (next) => {
    keyword.value = String(next ?? "");
  },
);

const KIND_META: Record<string, { label: string; color: string }> = {
  diary: { label: "日记", color: "#7f9db3" },
  experience: { label: "经验", color: "#e8c07d" },
  knowledge: { label: "知识条目", color: "#7fd1a8" },
};

const hits = computed(() =>
  keyword.value.trim() ? library.search(keyword.value.trim(), 30) : [],
);

const groups = computed(() =>
  (["diary", "experience", "knowledge"] as const).map((kind) => ({
    kind,
    label: KIND_META[kind]!.label,
    color: KIND_META[kind]!.color,
    rows: hits.value
      .filter((hit) => hit.kind === kind)
      .map((hit) => ({
        id: hit.id,
        title: hit.title,
        reason: hit.reason,
        to:
          kind === "diary"
            ? `/diaries/${hit.id}`
            : kind === "experience"
              ? `/experiences/${hit.id}`
              : `/knowledge/${hit.id}`,
      })),
  })),
);

const total = computed(() => hits.value.length);

function remember() {
  const q = keyword.value.trim();
  if (!q) return;
  history.value = [q, ...history.value.filter((item) => item !== q)].slice(0, 6);
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value));
  } catch {
    /* 忽略 */
  }
  router.replace({ path: "/search", query: { q } });
}

function applyKeyword(value: string) {
  keyword.value = value;
  remember();
}

function clear() {
  keyword.value = "";
  router.replace({ path: "/search" });
}

function askAgent() {
  const q = keyword.value.trim();
  if (!q) return;
  agent.newThread();
  agent.ask(q);
  router.push("/agent");
}
</script>
