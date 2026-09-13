<template>
  <div v-if="diary" class="fade-in">
    <span class="crumb" @click="router.push('/diaries')">← 返回日记列表</span>

    <div class="detail-head">
      <div class="row between wrap">
        <div style="min-width: 260px">
          <h1 class="h1" style="margin-bottom: 8px">{{ diary.title }}</h1>
          <div class="row wrap small faint">
            <span>{{ diary.date }}</span>
            <span>·</span>
            <span>{{ diary.kind }}</span>
            <span>·</span>
            <span>{{ diary.origin === "interview" ? "由采访生成" : "手动记录" }}</span>
          </div>
          <div class="tags" style="margin-top: 10px">
            <TagRow :tags="diary.tags" @pick="searchTag" />
          </div>
        </div>
        <div class="row">
          <button class="btn sm" @click="router.push(`/diaries/${diary.id}/edit`)">编辑</button>
          <button class="btn sm danger" @click="confirmDelete = true">删除</button>
        </div>
      </div>
    </div>

    <div class="split">
      <div class="panel">
        <div class="panel-head">
          <b>正文</b>
          <span class="spacer" />
          <span class="small faint">{{ relative(diary.createdAt) }}记录</span>
        </div>
        <div class="panel-body">
          <MarkdownView :text="diary.content" />
        </div>
      </div>

      <div class="stack">
        <div class="panel" style="cursor: pointer" @click="router.push(`/graph/local/${diary.id}?depth=2`)">
          <div class="panel-head"><b>🔗 查看关系图</b><span class="spacer" /><span class="small faint">查看该日记的局部关系网络</span></div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>这篇日记沉淀了什么</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <div>
              <div class="small faint" style="margin-bottom: 6px">经验卡 · {{ experiences.length }}</div>
              <div
                v-for="item in experiences"
                :key="item.id"
                class="kv-item"
                style="cursor: pointer; margin-bottom: 8px"
                @click="router.push(`/experiences/${item.id}`)"
              >
                <span>经验</span>
                <p>{{ item.title }}</p>
              </div>
              <p v-if="!experiences.length" class="small faint">还没有提炼出经验卡。</p>
            </div>
            <div>
              <div class="small faint" style="margin-bottom: 6px">关联知识 · {{ knowledges.length }}</div>
              <div
                v-for="item in knowledges"
                :key="item.id"
                class="kv-item"
                style="cursor: pointer; margin-bottom: 8px"
                @click="router.push(`/knowledge/${item.id}`)"
              >
                <span>{{ item.domain }} · {{ item.category }}</span>
                <p>{{ item.title }}</p>
              </div>
              <p v-if="!knowledges.length" class="small faint">还没有关联知识条目。</p>
            </div>
          </div>
        </div>

        <div class="panel ai-block">
          <div class="panel-head"><b>双链</b></div>
          <div class="panel-body stack" style="gap: 8px">
            <p v-if="!wikiLinks.length" class="small faint">正文里还没有 [[双链]]。</p>
            <button
              v-for="link in wikiLinks"
              :key="link"
              class="btn ghost"
              style="justify-content: flex-start; text-align: left"
              @click="openWiki(link)"
            >
              [[{{ link }}]] · 查看相关沉淀
            </button>
          </div>
        </div>
      </div>
    </div>

    <ModalDialog v-model="confirmDelete" title="删除这篇日记？" width="460px">
      <p class="small" style="margin: 0">
        将删除《{{ diary.title }}》，同时移除由它提炼出的经验卡（关联知识条目会保留）。该操作不可撤销。
      </p>
      <template #foot>
        <div class="row" style="justify-content: flex-end; width: 100%">
          <button class="btn" @click="confirmDelete = false">取消</button>
          <button class="btn danger" @click="removeDiary">确认删除</button>
        </div>
      </template>
    </ModalDialog>
  </div>

  <EmptyState v-else icon="🕳" title="找不到这篇日记" hint="它可能已被删除，回到列表看看其它记录。">
    <button class="btn primary sm" @click="router.push('/diaries')">返回日记列表</button>
  </EmptyState>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";
import MarkdownView from "@/components/base/MarkdownView.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import TagRow from "@/components/base/TagRow.vue";
import { relative } from "@/lib/format";
import { extractWikiLinks } from "@/lib/markdown";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const confirmDelete = ref(false);

const diaryId = computed(() => String(route.params.id ?? ""));
const diary = computed(() => library.diaryById(diaryId.value));
const experiences = computed(() => library.experiences.filter((e) => e.diaryId === diaryId.value));
const knowledges = computed(() =>
  library.knowledge.filter((k) => k.id && diary.value?.knowledgeIds.includes(k.id)),
);
const wikiLinks = computed(() => (diary.value ? extractWikiLinks(diary.value.content) : []));

function removeDiary() {
  const target = diary.value;
  if (!target) return;
  library.deleteDiary(target.id);
  confirmDelete.value = false;
  ui.toast("日记与关联经验卡已删除", "warn");
  router.push("/diaries");
}

function searchTag(tag: string) {
  router.push({ name: "search", query: { q: tag } });
}

function openWiki(title: string) {
  const hit = library.knowledgeByTitle(title);
  if (hit) router.push(`/knowledge/${hit.id}`);
  else ui.toast(`还没有「${title}」这条知识，去知识库新建一条吧`, "info");
}
</script>
