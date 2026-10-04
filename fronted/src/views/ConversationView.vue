<template>
  <div v-if="isThread" class="fade-in interview-page thread-dock">
    <div class="interview-head">
      <div class="grow">
        <h1 class="h1">{{ store.current?.title || "对话" }}</h1>
        <p class="lede" style="margin-bottom: 0">
          讲一件事，或问问过去的记录。没听懂的地方可以让助手补充，只有你点过同意才会写入。
        </p>
      </div>
    </div>

    <div class="interview-main">
      <div ref="scrollRef" class="chat-scroll">
        <ChatMessage
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :show-who="Boolean(message.payload?.supplement)"
          :who-label="message.payload?.supplement ? '模型补充' : 'MemoAgent'"
        >
          <div v-if="message.refs.length" class="stack" style="gap: 6px">
            <button
              v-for="ref in message.refs"
              :key="`${ref.type}-${ref.id}`"
              type="button"
              class="btn ghost sm"
              style="justify-content: flex-start"
              @click="openRef(ref)"
            >
              {{ refLabel(ref.type) }} · {{ ref.title }}
            </button>
          </div>

          <div v-if="message.payload?.supplement" class="row" style="gap: 8px">
            <template v-if="!message.payload?.choice">
              <button type="button" class="btn sm primary" @click="choose(message.id, 'accept')">写入知识库</button>
              <button type="button" class="btn sm" @click="choose(message.id, 'skip')">先不写</button>
            </template>
            <span v-else class="small faint">{{ message.payload.choice === "accept" ? "已同意写入" : "不写入" }}</span>
          </div>

          <div v-if="message.payload?.links?.length" class="stack" style="gap: 8px">
            <div v-for="link in message.payload?.links ?? []" :key="link.knowledgeId" class="row between wrap">
              <span class="small">{{ link.title }}</span>
              <span v-if="link.choice" class="small faint">{{ link.choice === "accept" ? "已关联" : "不关联" }}</span>
              <span v-else class="row" style="gap: 8px">
                <button type="button" class="btn sm primary" @click="choose(message.id, 'accept', link.knowledgeId)">关联到这次</button>
                <button type="button" class="btn sm" @click="choose(message.id, 'skip', link.knowledgeId)">先不关联</button>
              </span>
            </div>
          </div>
        </ChatMessage>

        <div v-if="canDraft && !store.sending" class="bubble-row">
          <div class="avatar">✦</div>
          <div class="stack" style="gap: 8px; max-width: min(680px, 82%)">
            <div class="bubble">要我把这次整理成记录吗？确认后可以再改标题和正文。</div>
            <button type="button" class="btn sm primary" :disabled="drafting" @click="organize">
              {{ drafting ? "整理中…" : "整理" }}
            </button>
          </div>
        </div>

        <div v-if="showTyping" class="bubble-row">
          <div class="avatar">✦</div>
          <div class="bubble typing"><i /><i /><i /><span class="faint small">正在理解你刚说的内容…</span></div>
        </div>
      </div>

      <div class="composer">
        <div class="dock-composer">
          <textarea
            v-model="input"
            rows="1"
            placeholder="给 MemoAgent 发送消息"
            @keydown.enter.exact.prevent="submitThread"
          />
          <button
            class="dock-send"
            type="button"
            :disabled="!store.sending && !input.trim()"
            :aria-label="store.sending ? '停止' : '发送'"
            @click="store.sending ? store.stop() : submitThread()"
          >
            <span v-if="store.sending" class="dock-stop" />
            <template v-else>↑</template>
          </button>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="fade-in">
    <section class="hero">
      <h1>今天，<em>发生了什么？</em></h1>
      <p>和 AI 聊聊就好。讲一件事，或问问过去的记录，再整理成日记、经验和知识。</p>
      <div class="dock-composer">
        <textarea
          v-model="input"
          rows="1"
          placeholder="给 MemoAgent 发送消息"
          @keydown.enter.exact.prevent="submitHome"
        />
        <button
          class="dock-send"
          type="button"
          :disabled="!store.sending && !input.trim()"
          :title="store.sending ? '停止' : '发送'"
          :aria-label="store.sending ? '停止' : '发送'"
          @click="store.sending ? store.stop() : submitHome()"
        >
          <span v-if="store.sending" class="dock-stop" />
          <template v-else>↑</template>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { isAbortError } from "@/api/conversation";
import type { ConversationRef } from "@/api/conversation";
import ChatMessage from "@/components/chat/ChatMessage.vue";
import { useConversationStore } from "@/stores/conversation";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const router = useRouter();
const store = useConversationStore();
const ui = useUiStore();
const input = ref("");
const drafting = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

const isThread = computed(() => route.name === "conversation");

const messages = computed(() =>
  (store.current?.messages ?? [])
    .filter((message) => message.role !== "assistant" || Boolean(message.text?.trim()))
    .map((message) => ({
      ...message,
      role: message.role === "assistant" ? ("ai" as const) : ("user" as const),
      refs: message.payload?.refs ?? [],
    })),
);

const showTyping = computed(() => {
  if (!store.sending) return false;
  const latest = [...(store.current?.messages ?? [])].reverse().find((item) => item.role === "assistant");
  return !latest?.text?.trim();
});

const canDraft = computed(() => {
  const current = store.current;
  if (!current || current.status === "committed" || store.sending) return false;
  const latest = [...current.messages].reverse().find((item) => item.role === "assistant");
  return Boolean(latest?.payload?.ready);
});

function refLabel(type: ConversationRef["type"]) {
  if (type === "diary") return "日记";
  if (type === "experience") return "经验";
  return "知识";
}

function openRef(ref: ConversationRef) {
  if (ref.type === "diary") router.push(`/diaries/${ref.id}`);
  else if (ref.type === "experience") router.push(`/experiences/${ref.id}`);
  else router.push(`/knowledge/${ref.id}`);
}

async function submitHome() {
  const text = input.value.trim();
  if (!text || store.sending) return;
  input.value = "";
  try {
    const id = await store.openFromHome(text);
    await router.push(`/c/${id}`);
    await store.deliver(id, text);
  } catch (error) {
    if (isAbortError(error)) return;
    store.sending = false;
    ui.toast(errorMessage(error, "发送失败"), "error");
  }
}

async function submitThread() {
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  try {
    await store.send(text);
  } catch (error) {
    if (isAbortError(error)) return;
    ui.toast(errorMessage(error, "发送失败"), "error");
  }
}

async function organize() {
  if (!store.current) return;
  drafting.value = true;
  try {
    await store.draft();
    router.push(`/diaries/draft/${store.current.id}`);
  } catch (error) {
    ui.toast(errorMessage(error, "整理失败"), "error");
  } finally {
    drafting.value = false;
  }
}

async function choose(messageId: number, choice: "accept" | "skip", knowledgeId?: number) {
  try {
    await store.choose(messageId, choice, knowledgeId);
  } catch (error) {
    ui.toast(errorMessage(error, "选择没有保存"), "error");
  }
}

async function syncRoute() {
  if (route.name !== "conversation") return;
  const id = String(route.params.id ?? "");
  if (!id || String(store.current?.id ?? "") === id) return;
  await store.open(id);
}

watch(
  () => route.fullPath,
  () => {
    void syncRoute().catch((error) => ui.toast(errorMessage(error, "对话加载失败"), "error"));
  },
);

watch(
  () => (store.current?.messages ?? []).map((item) => item.text).join("\n").length,
  async () => {
    await nextTick();
    const el = scrollRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  },
);

onMounted(async () => {
  try {
    await store.refresh();
    const opening = typeof route.query.opening === "string" ? route.query.opening.trim() : "";
    if (opening) {
      await router.replace({ path: "/" });
      const id = await store.openFromHome(opening);
      await router.push(`/c/${id}`);
      await store.deliver(id, opening);
      return;
    }
    await syncRoute();
  } catch (error) {
    ui.toast(errorMessage(error, "对话加载失败"), "error");
  }
});
</script>

<style scoped>
.thread-dock {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: calc(100vh - var(--topbar-h));
  margin: calc(var(--main-pad-y) * -1) calc(var(--main-pad-x) * -1) -56px;
  padding: var(--main-pad-y) var(--main-pad-x) 16px;
}

.thread-dock .interview-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.thread-dock .chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.thread-dock .composer {
  position: static;
  flex: none;
  padding-top: 8px;
  background: linear-gradient(180deg, transparent, var(--paper) 36%);
}

.dock-composer {
  width: min(720px, 100%);
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 18px;
  border: 1px solid var(--line-2);
  border-radius: 28px;
  background: var(--surface);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.28);
}

.dock-composer textarea {
  flex: 1;
  min-width: 0;
  min-height: 28px;
  max-height: 160px;
  margin: 0;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 15px;
  line-height: 1.5;
  resize: none;
}

.dock-composer textarea:focus {
  outline: none;
}

.dock-send {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--ink);
  color: var(--accent-ink);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.dock-send:hover:not(:disabled) {
  background: var(--accent-2);
}

.dock-send:disabled {
  opacity: 0.28;
  cursor: not-allowed;
}

.dock-stop {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  background: currentColor;
}

@media (max-width: 860px) {
  .thread-dock {
    margin-left: -16px;
    margin-right: -16px;
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
