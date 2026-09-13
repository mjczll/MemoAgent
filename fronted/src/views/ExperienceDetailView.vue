<template>
  <div v-if="item" class="fade-in">
    <span class="crumb" @click="router.push('/experiences')">← 返回经验列表</span>

    <div class="detail-head">
      <div class="row between wrap">
        <div style="min-width: 260px">
          <h1 class="h1" style="margin-bottom: 8px">{{ item.title }}</h1>
          <div class="row wrap small faint">
            <span>{{ item.domain }}</span>
            <span>·</span>
            <span>{{ item.createdAt.slice(0, 10) }} 沉淀</span>
          </div>
          <div class="tags" style="margin-top: 10px">
            <TagRow :tags="item.tags" @pick="searchTag" />
          </div>
        </div>
        <div class="row">
          <button class="btn sm" @click="router.push(`/diaries/${item.diaryId}`)">查看来源日记</button>
        </div>
      </div>
    </div>

    <div class="split">
      <div class="stack">
        <div class="panel" style="cursor: pointer" @click="router.push(`/graph/local/${item.id}?depth=2`)">
          <div class="panel-head"><b>🔗 查看关系图</b><span class="spacer" /><span class="small faint">查看该经验的局部关系网络</span></div>
        </div>

        <!-- 沉淀路径：日记 → 经验 -->
        <div v-if="pathNodes.length" class="panel">
          <div class="panel-head"><b>沉淀路径</b><span class="spacer" /><span class="small faint">日记 → 经验 → 知识</span></div>
          <div class="panel-body">
            <StackPath :nodes="pathNodes" />
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>结构化结论</b><span class="spacer" /><span class="small faint">复制即可复用到下次踩坑</span></div>
          <div class="panel-body kv">
            <div class="kv-item"><span>问题</span><p>{{ item.problem }}</p></div>
            <div class="kv-item"><span>原因</span><p>{{ item.cause }}</p></div>
            <div class="kv-item"><span>解决方案</span><p>{{ item.solution }}</p></div>
          </div>
        </div>

        <div class="panel ai-block">
          <div class="panel-head"><b>经验 · 一句话带走</b></div>
          <div class="panel-body">
            <MarkdownView :text="`> ${item.lesson}`" />
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>关联知识</b><span class="spacer" /><span class="badge">{{ knowledges.length }}</span></div>
          <div class="panel-body stack" style="gap: 8px">
            <div
              v-for="k in knowledges"
              :key="k.id"
              class="kv-item"
              style="cursor: pointer"
              @click="router.push(`/knowledge/${k.id}`)"
            >
              <span>{{ k.domain }} · {{ k.category }} · {{ k.mastery }}</span>
              <p>{{ k.title }}</p>
            </div>
            <p v-if="!knowledges.length" class="small faint">这张经验卡还没有关联知识条目。</p>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>来源</b></div>
          <div class="panel-body">
            <div class="kv-item" style="cursor: pointer" @click="router.push(`/diaries/${item.diaryId}`)">
              <span>日记</span>
              <p>{{ diary ? diary.title : "来源日记已删除" }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <EmptyState v-else icon="🕳" title="找不到这张经验卡" hint="它可能随来源日记一起被删除了。">
    <button class="btn primary sm" @click="router.push('/experiences')">返回经验列表</button>
  </EmptyState>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import MarkdownView from "@/components/base/MarkdownView.vue";
import StackPath from "@/components/base/StackPath.vue";
import TagRow from "@/components/base/TagRow.vue";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const item = computed(() => library.experienceById(String(route.params.id ?? "")));
const diary = computed(() => (item.value ? library.diaryById(item.value.diaryId) : undefined));
const knowledges = computed(() =>
  item.value ? library.knowledge.filter((k) => item.value?.knowledgeIds.includes(k.id)) : [],
);

/** 沉淀路径：日记 → 经验（关联知识可选显示） */
const pathNodes = computed(() => {
  if (!item.value) return [];
  const nodes: Array<{ id: string; kind: "diary" | "experience" | "knowledge"; title: string }> = [];
  if (diary.value) nodes.push({ id: diary.value.id, kind: "diary", title: diary.value.title });
  nodes.push({ id: item.value.id, kind: "experience", title: item.value.title });
  knowledges.value.slice(0, 2).forEach((k) => nodes.push({ id: k.id, kind: "knowledge", title: k.title }));
  return nodes;
});

function searchTag(tag: string) {
  router.push({ name: "search", query: { q: tag } });
}
</script>
