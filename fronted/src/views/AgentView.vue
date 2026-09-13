<template>
  <div class="fade-in agent-page">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">问 Agent</h1>
        <p class="lede" style="margin-bottom: 0">
          用自然语言问过去的事，Agent 会翻你的日记、经验与知识条目作答，并附上引用来源。
        </p>
      </div>
      <button class="btn" @click="newThread">新建会话</button>
    </div>

    <div class="agent-split">
      <div class="agent-side">
        <div class="panel">
          <div class="panel-head"><b>会话</b><span class="spacer" /><span class="badge">{{ store.threads.length }}</span></div>
          <div class="panel-body" style="padding: 6px">
            <div class="list">
              <div
                v-for="thread in store.threads"
                :key="thread.id"
                class="list-row"
                style="padding: 10px 12px"
                @click="store.switchThread(thread.id)"
              >
                <div class="row between">
                  <div class="title" style="min-width: 0">
                    <b class="ellipsis">{{ thread.title }}</b>
                  </div>
                  <button
                    class="icon-btn"
                    style="width: 22px; height: 22px; font-size: 11px"
                    title="删除会话"
                    @click.stop="removeThread(thread.id)"
                  >
                    ✕
                  </button>
                </div>
                <div class="meta">
                  <span>{{ relative(thread.updatedAt) }}</span>
                  <span>{{ (store.messagesMap[thread.id] ?? []).length }} 条消息</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="agent-main">
        <div ref="scrollRef" class="chat-scroll">
          <AgentBubble v-for="message in store.activeMessages" :key="message.id" :message="message" />

          <div v-if="store.thinking" class="bubble-row">
            <div class="avatar">✦</div>
            <div class="bubble typing">
              <i /><i /><i />
              <span class="faint small">正在检索你的知识资产…</span>
            </div>
          </div>
        </div>

        <div class="composer">
          <div class="composer-box">
            <textarea
              v-model="input"
              class="textarea"
              placeholder="比如：我以前是怎么处理缓存击穿的？"
              @keydown.enter.exact.prevent="submit"
            />
            <button class="btn primary" :disabled="store.thinking || !input.trim()" @click="submit">提问</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import AgentBubble from "@/components/agent/AgentBubble.vue";
import { relative } from "@/lib/format";
import { useAgentStore } from "@/stores/agent";
import { useUiStore } from "@/stores/ui";

const store = useAgentStore();
const ui = useUiStore();

const input = ref("");
const scrollRef = ref<HTMLElement | null>(null);

onMounted(() => {
  const before = store.threads.length;
  store.hydrate();
  const question = sessionStorage.getItem("memoagent:question");
  if (question) {
    sessionStorage.removeItem("memoagent:question");
    store.ask(question);
  }
  if (!before && !store.threads.length) store.newThread();
  scrollToEnd();
});

watch(
  () => [store.activeMessages.length, store.thinking, store.activeThreadId],
  () => scrollToEnd(),
);

function scrollToEnd() {
  nextTick(() => {
    const el = scrollRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function submit() {
  const text = input.value.trim();
  if (!text) return;
  store.ask(text);
  input.value = "";
}

function ask(text: string) {
  if (store.thinking) return;
  store.ask(text);
}

function newThread() {
  store.newThread();
  ui.toast("已开启新会话", "info");
}

function removeThread(id: string) {
  store.deleteThread(id);
  ui.toast("会话已删除", "info");
}
</script>

<style scoped>
.agent-page {
  height: calc(100vh - 90px);
  display: flex;
  flex-direction: column;
}

.agent-split {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 14px;
}

.agent-side {
  min-width: 0;
  overflow-y: auto;
}

.agent-main {
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

.ellipsis {
  display: inline-block;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 880px) {
  .agent-page {
    height: auto;
  }
  .agent-split {
    grid-template-columns: 1fr;
  }
}
</style>
