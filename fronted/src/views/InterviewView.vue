<template>
  <div class="fade-in interview-page">
    <div class="interview-head">
      <div class="grow">
        <h1 class="h1">采访现场</h1>
        <p class="lede" style="margin-bottom: 0">
          AI 会像访谈一样问你，把这段对话整理成日记草稿，并给出经验与知识建议。
        </p>
      </div>
      <div class="row">
        <button class="btn" @click="restart">重新开始</button>
        <button class="btn primary" :disabled="!canGenerate" @click="generate">整理成日记</button>
      </div>
    </div>

    <div class="interview-grid">
      <div class="interview-side">
        <div class="panel">
          <div class="panel-head"><b>会话记录</b></div>
          <div class="panel-body stack" style="gap: 6px">
            <div
              v-for="(answer, index) in store.answers"
              :key="index"
              class="round-row"
              :class="{ active: index === store.stepIndex }"
              @click="jumpTo(index)"
            >
              <span class="round-idx">{{ index + 1 }}</span>
              <span class="round-title">{{ steps[index]?.label ?? "补充" }}</span>
              <span class="round-hint" :class="answer ? 'ok' : 'faint'">
                {{ answer ? "已答" : "待回答" }}
              </span>
            </div>
            <p v-if="!store.answers.length" class="small faint" style="margin: 0">
              在下方讲讲今天想记录的事，比如一次排查、一次联调、一次复盘。
            </p>
          </div>
        </div>
      </div>

      <div class="interview-main">
        <div ref="scrollRef" class="chat-scroll">
          <ChatMessage
            v-for="message in store.messages"
            :key="message.id"
            :message="message"
          >
            <div v-if="message.action === 'generate-diary'" class="card flat">
              <div class="row between">
                <div>
                  <b class="small">草稿已就绪</b>
                  <p class="small faint" style="margin: 2px 0 0">
                    {{ store.draft ? store.draft.title : "还没生成" }} ·
                    预计沉淀 {{ store.draft?.suggestedKnowledge.length ?? 0 }} 条知识
                  </p>
                </div>
                <div class="row">
                  <button class="btn sm" @click="showDraft = true">预览草稿</button>
                  <button class="btn sm primary" @click="save">保存到日记</button>
                </div>
              </div>
            </div>
          </ChatMessage>

          <div v-if="store.thinking" class="bubble-row">
            <div class="avatar">✦</div>
            <div class="bubble typing"><i /><i /><i /><span class="faint small">正在理解你刚说的内容…</span></div>
          </div>
        </div>

        <div class="composer">
          <div class="composer-box">
            <textarea
              v-model="input"
              class="textarea"
              :placeholder="placeholder"
              @keydown.enter.exact.prevent="submit"
            />
            <button class="btn primary" :disabled="store.thinking || !input.trim()" @click="submit">发送</button>
          </div>
        </div>
      </div>
    </div>

    <ModalDialog v-model="showDraft" title="日记草稿预览" width="760px">
      <div v-if="store.draft" class="stack">
        <div class="field">
          <label>标题</label>
          <input v-model="store.draft.title" class="input" />
        </div>
        <div class="row wrap small faint">
          <span>日期：{{ store.draft.date }}</span>
          <span>类型：{{ store.draft.kind }}</span>
          <span>标签：{{ store.draft.tags.join(" / ") }}</span>
        </div>
        <div class="panel">
          <div class="panel-head"><b>正文</b><span class="spacer" /><span class="small faint">可直接编辑</span></div>
          <div class="panel-body">
            <textarea v-model="store.draft.content" class="textarea" style="min-height: 180px" />
          </div>
        </div>
        <div class="panel ai-block">
          <div class="panel-head"><b>AI 结构化提炼</b></div>
          <div class="panel-body kv">
            <div class="kv-item"><span>问题</span><p>{{ store.draft.extraction.problem }}</p></div>
            <div class="kv-item"><span>原因</span><p>{{ store.draft.extraction.cause }}</p></div>
            <div class="kv-item"><span>解决方案</span><p>{{ store.draft.extraction.solution }}</p></div>
            <div class="kv-item"><span>经验</span><p>{{ store.draft.extraction.lesson }}</p></div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><b>建议关联知识</b><span class="spacer" /><span class="small faint">命中已有条目则复用，否则新建</span></div>
          <div class="panel-body stack" style="gap: 8px">
            <div v-for="(item, index) in store.draft.suggestedKnowledge" :key="index" class="kv-item">
              <span>{{ item.existingId ? "已有知识" : "将新建知识" }}</span>
              <p>{{ item.title }}</p>
              <p class="small faint" style="margin-top: 2px">{{ item.reason }}</p>
            </div>
          </div>
        </div>
      </div>
      <template #foot>
        <div class="row between" style="width: 100%">
          <button class="btn ghost" @click="regenerate">重新整理</button>
          <div class="row">
            <button class="btn" @click="showDraft = false">稍后再说</button>
            <button class="btn primary" @click="save">保存到日记库</button>
          </div>
        </div>
      </template>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import ChatMessage from "@/components/chat/ChatMessage.vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import { errorMessage } from "@/api/http";
import { INTERVIEW_STEPS } from "@/mock";
import { useInterviewStore } from "@/stores/interview";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const router = useRouter();
const store = useInterviewStore();
const library = useLibraryStore();
const ui = useUiStore();

const steps = INTERVIEW_STEPS;
const input = ref("");
const showDraft = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

const canGenerate = computed(() => store.answers.length >= INTERVIEW_STEPS.length && !store.draft);
const placeholder = computed(() =>
  store.stepIndex === 0
    ? "讲讲最近发生的一件值得记录的事…"
    : `回答「${steps[Math.min(store.stepIndex, steps.length - 1)]?.label ?? "补充"}」这一步…`,
);

onMounted(() => {
  store.hydrate();
  const opening = sessionStorage.getItem("memoagent:opening");
  if (opening) {
    sessionStorage.removeItem("memoagent:opening");
    store.reset(opening);
  }
  scrollToEnd();
});

watch(
  () => [store.messages.length, store.thinking],
  () => scrollToEnd(),
);

function scrollToEnd() {
  nextTick(() => {
    const el = scrollRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function jumpTo(index: number) {
  // 找到第 index 个回答对应的消息气泡并滚动到视图中
  const userMsg = store.messages.filter((m) => m.role === "user")[index];
  if (!userMsg) return;
  nextTick(() => {
    const el = document.querySelector<HTMLElement>(`[data-msg-id="${userMsg.id}"]`);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
  });
}

function submit() {
  const text = input.value.trim();
  if (!text) return;
  store.send(text);
  input.value = "";
}

function generate() {
  if (store.regenerateDraft()) {
    ui.toast("草稿已整理完成，可预览后保存");
    showDraft.value = true;
  }
}

function regenerate() {
  if (store.regenerateDraft()) ui.toast("已按最新回答重新整理草稿");
}

async function save() {
  const draft = store.draft;
  if (!draft) return;
  try {
    const created = await library.saveDraft(draft);
    store.markDraftSaved(created.diaryId);
    showDraft.value = false;
    ui.toast(
      `已生成日记，并沉淀 1 张经验卡${created.newKnowledgeIds.length ? `、新建 ${created.newKnowledgeIds.length} 条知识` : ""}`,
    );
    router.push(`/diaries/${created.diaryId}`);
  } catch (error) {
    ui.toast(errorMessage(error, "保存日记失败"), "error");
  }
}

function restart() {
  store.reset();
  ui.toast("已开始一次新的采访", "info");
}
</script>

<style scoped>
.interview-page {
  height: calc(100vh - 90px);
  display: flex;
  flex-direction: column;
}

.interview-head {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
}

.interview-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
}

.interview-side {
  min-width: 0;
  overflow-y: auto;
}

.interview-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.composer {
  margin-top: 10px;
}

.round-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.14s, color 0.14s;
}

.round-row:hover {
  background: var(--hover);
}

.round-row.active {
  background: var(--hover-strong);
  color: var(--ink);
}

.round-idx {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid var(--line);
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--mute);
}

.round-row.active .round-idx {
  border-color: var(--accent-line);
  color: var(--ink);
}

.round-title {
  flex: 1;
  font-size: 12.5px;
  color: inherit;
}

.round-hint {
  font-size: 10.5px;
}

.round-hint.ok {
  color: var(--ok);
}

.round-hint.faint {
  color: var(--faint);
}

@media (max-width: 880px) {
  .interview-page {
    height: auto;
  }
  .interview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
