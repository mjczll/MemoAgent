<template>
  <div v-if="item" class="fade-in">
    <span class="crumb" @click="router.push('/knowledge')">← 返回知识库</span>

    <div class="detail-head">
      <div class="row between wrap">
        <div style="min-width: 260px">
          <h1 class="h1" style="margin-bottom: 8px">{{ item.title }}</h1>
          <div class="row wrap small faint">
            <span>{{ item.domain }}</span>
            <span>·</span>
            <span>{{ item.category }}</span>
            <span>·</span>
            <span>{{ relative(item.updatedAt) }}更新</span>
          </div>
          <div class="tags" style="margin-top: 10px">
            <TagRow :tags="item.tags" @pick="searchTag" />
          </div>
        </div>
        <div class="row">
          <button class="btn sm" @click="openEdit">编辑</button>
        </div>
      </div>
    </div>

    <div class="split">
      <div class="stack">
        <div class="panel" style="cursor: pointer" @click="router.push(`/graph/local/${item.id}?depth=2`)">
          <div class="panel-head"><b>🔗 查看关系图</b><span class="spacer" /><span class="small faint">查看该知识的局部关系网络</span></div>
        </div>

        <!-- 沉淀路径：日记 → 经验 → 知识 -->
        <div v-if="pathNodes.length" class="panel">
          <div class="panel-head"><b>沉淀路径</b><span class="spacer" /><span class="small faint">日记 → 经验 → 知识</span></div>
          <div class="panel-body">
            <StackPath :nodes="pathNodes" />
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>正文</b><span class="spacer" /><span class="small faint">支持 Markdown 与 [[双链]]</span></div>
          <div class="panel-body">
            <MarkdownView :text="item.content" />
          </div>
        </div>

        <!-- 方案对比：仅当来源经验 ≥ 2 时显示，提示已采用互斥锁方案 -->
        <div v-if="compareBlock" class="panel">
          <div class="panel-head"><b>新旧方案对比</b><span class="spacer" /><span class="small faint">取自相关经验中的处理方式</span></div>
          <div class="panel-body">
            <CompareRow v-bind="compareBlock" />
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>掌握度</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <div class="seg" style="align-self: flex-start">
              <button
                v-for="m in MASTERIES"
                :key="m"
                :class="{ on: item.mastery === m }"
                @click="setMastery(m)"
              >
                {{ m }}
              </button>
            </div>
            <p class="small faint" style="margin: 0">
              标记为「已掌握」的知识会计入首页掌握率。
            </p>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <b>相关知识</b>
            <span class="spacer" />
            <span class="badge">{{ related.length }}</span>
          </div>
          <div class="panel-body stack" style="gap: 8px">
            <div
              v-for="k in related"
              :key="k.id"
              class="kv-item"
              style="cursor: pointer"
              @click="router.push(`/knowledge/${k.id}`)"
            >
              <span>{{ k.category }} · {{ k.mastery }}</span>
              <p>{{ k.title }}</p>
            </div>
            <p v-if="!related.length" class="small faint">还没有互链，点击下方按钮补一条关系。</p>
            <button class="btn sm" @click="openLink">添加互链</button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>来源经验</b><span class="spacer" /><span class="badge">{{ sources.length }}</span></div>
          <div class="panel-body stack" style="gap: 8px">
            <div
              v-for="e in sources"
              :key="e.id"
              class="kv-item"
              style="cursor: pointer"
              @click="router.push(`/experiences/${e.id}`)"
            >
              <span>经验卡</span>
              <p>{{ e.title }}</p>
            </div>
            <p v-if="!sources.length" class="small faint">这条知识还没有关联的经验来源。</p>
          </div>
        </div>
      </div>
    </div>

    <ModalDialog v-model="showEdit" title="编辑知识正文" width="760px">
      <div class="stack">
        <div class="field">
          <label>标题</label>
          <input v-model="editForm.title" class="input" />
        </div>
        <div class="field">
          <label>摘要</label>
          <input v-model="editForm.summary" class="input" />
        </div>
        <div class="field">
          <label>正文</label>
          <textarea v-model="editForm.content" class="textarea" style="min-height: 240px" />
        </div>
      </div>
      <template #foot>
        <div class="row" style="justify-content: flex-end; width: 100%">
          <button class="btn" @click="showEdit = false">取消</button>
          <button class="btn primary" @click="saveEdit">保存修改</button>
        </div>
      </template>
    </ModalDialog>

    <ModalDialog v-model="showLink" title="添加知识互链" width="560px">
      <div class="stack" style="max-height: 46vh; overflow-y: auto">
        <button
          v-for="k in linkCandidates"
          :key="k.id"
          class="list-row"
          style="width: 100%; text-align: left"
          @click="addLink(k.id)"
        >
          <div class="title"><b>{{ k.title }}</b><span class="badge">{{ k.category }}</span></div>
          <div class="meta"><span>{{ k.domain }}</span></div>
        </button>
        <p v-if="!linkCandidates.length" class="small faint">其它知识都已经建立互链了。</p>
      </div>
    </ModalDialog>
  </div>

  <EmptyState v-else icon="🕳" title="找不到这条知识" hint="它可能已被删除，回到知识库看看其它条目。">
    <button class="btn primary sm" @click="router.push('/knowledge')">返回知识库</button>
  </EmptyState>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import CompareRow from "@/components/base/CompareRow.vue";
import EmptyState from "@/components/base/EmptyState.vue";
import MarkdownView from "@/components/base/MarkdownView.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import StackPath from "@/components/base/StackPath.vue";
import TagRow from "@/components/base/TagRow.vue";
import { relative } from "@/lib/format";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";
import type { Mastery } from "@/types";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();
const ui = useUiStore();

const MASTERIES: Mastery[] = ["已掌握", "部分掌握", "待补充"];

const showEdit = ref(false);
const showLink = ref(false);

const editForm = reactive({ title: "", summary: "", content: "" });

const itemId = computed(() => String(route.params.id ?? ""));
const item = computed(() => library.knowledgeById(itemId.value));
const related = computed(() =>
  item.value ? library.knowledge.filter((k) => item.value?.relatedIds.includes(k.id)) : [],
);
const sources = computed(() =>
  item.value ? library.experiences.filter((e) => item.value?.sourceExperienceIds.includes(e.id)) : [],
);
const linkCandidates = computed(() =>
  library.knowledge.filter(
    (k) => k.id !== itemId.value && !item.value?.relatedIds.includes(k.id),
  ),
);

/** 沉淀路径：日记 → 经验 → 知识 */
const pathNodes = computed(() => {
  if (!item.value) return [];
  const nodes: Array<{ id: string; kind: "diary" | "experience" | "knowledge"; title: string }> = [];
  sources.value.forEach((e) => {
    const d = library.diaryById(e.diaryId);
    if (d) nodes.push({ id: d.id, kind: "diary", title: d.title });
    nodes.push({ id: e.id, kind: "experience", title: e.title });
  });
  nodes.push({ id: item.value.id, kind: "knowledge", title: item.value.title });
  // 简单去重
  const seen = new Set<string>();
  return nodes.filter((n) => (seen.has(`${n.kind}:${n.id}`) ? false : (seen.add(`${n.kind}:${n.id}`), true)));
});

/** 方案对比：来源经验 ≥ 2 时生成简易对比 */
const compareBlock = computed(() => {
  if (sources.value.length < 2) return null;
  return {
    left: { title: "传统方案", items: ["直接同步重建缓存", "依赖 TTL 过期", "无并发保护"] },
    right: { title: "本次采用", items: ["互斥锁控制重建", "失败请求短暂等待", "源头热点 Key 永不过期"] },
    best: "right" as const,
  };
});

watch(
  item,
  (value) => {
    if (!value) return;
    editForm.title = value.title;
    editForm.summary = value.summary;
    editForm.content = value.content;
  },
  { immediate: true },
);

function setMastery(m: Mastery) {
  library.updateKnowledge(itemId.value, { mastery: m });
  ui.toast(`已标记为「${m}」`);
}

function openEdit() {
  showEdit.value = true;
}

function saveEdit() {
  library.updateKnowledge(itemId.value, {
    title: editForm.title.trim(),
    summary: editForm.summary.trim(),
    content: editForm.content,
  });
  showEdit.value = false;
  ui.toast("知识已更新");
}

function openLink() {
  showLink.value = true;
}

function addLink(id: string) {
  const current = item.value;
  if (!current) return;
  library.updateKnowledge(current.id, { relatedIds: [...current.relatedIds, id] });
  const other = library.knowledgeById(id);
  if (other && !other.relatedIds.includes(current.id)) {
    library.updateKnowledge(other.id, { relatedIds: [...other.relatedIds, current.id] });
  }
  showLink.value = false;
  ui.toast("已建立双向互链");
}

function searchTag(tag: string) {
  router.push({ name: "search", query: { q: tag } });
}
</script>
