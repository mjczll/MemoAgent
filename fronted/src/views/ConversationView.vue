<template>
  <div v-if="isThread" class="fade-in interview-page thread-dock">
    <div class="interview-main">
      <div ref="scrollRef" class="chat-scroll">
        <ChatMessage
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :show-who="Boolean(message.payload?.supplement)"
          :who-label="message.payload?.supplement ? '模型补充' : 'MemoAgent'"
        >
          <template #above>
            <button
              v-if="message.refs.length"
              type="button"
              class="ref-pill"
              :aria-label="`查看 ${message.refs.length} 条引用`"
              @click="openRefDrawer(message.refs)"
            >
              <SvgIcon class="ref-pill-ico" name="search" />
              搜索到 {{ message.refs.length }} 条相关引用
            </button>
          </template>

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
            ref="composerRef"
            v-model="input"
            rows="1"
            placeholder="给 MemoAgent 发送消息"
            @keydown.enter.exact.prevent="submitThread"
            @input="autosize"
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
          ref="composerRef"
          v-model="input"
          rows="1"
          placeholder="给 MemoAgent 发送消息"
          @keydown.enter.exact.prevent="submitHome"
          @input="autosize"
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

  <Teleport to="body">
    <div v-if="refDrawer.length" class="ref-mask" @click.self="closeRefDrawer">
      <aside class="ref-drawer" role="dialog" aria-label="引用">
        <header class="ref-drawer-head">
          <b>搜索结果</b>
          <button type="button" class="ref-close" aria-label="关闭" @click="closeRefDrawer">✕</button>
        </header>
        <div class="ref-drawer-body">
          <button
            v-for="item in refDrawer"
            :key="`${item.type}-${item.id}`"
            type="button"
            class="ref-card"
            @click="openRef(item)"
          >
            <div class="ref-card-head">
              <span class="ref-kind">{{ refLabel(item.type) }}</span>
              <b class="ref-title">{{ item.title }}</b>
            </div>
            <p v-if="refText(item)" class="ref-excerpt">{{ refText(item) }}</p>
          </button>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { isAbortError } from "@/api/conversation";
import type { ConversationRef } from "@/api/conversation";
import ChatMessage from "@/components/chat/ChatMessage.vue";
import SvgIcon from "@/components/base/SvgIcon.vue";
import { useConversationStore } from "@/stores/conversation";
import { useLibraryStore } from "@/stores/library";
import { useUiStore } from "@/stores/ui";

const route = useRoute();
const router = useRouter();
const store = useConversationStore();
const library = useLibraryStore();
const ui = useUiStore();
const input = ref("");
const composerRef = ref<HTMLTextAreaElement | null>(null);
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

/** 按内容高度调整输入框，到 CSS 的 max-height 就停住，超出部分改为内部滚动 */
function applySize(el: HTMLTextAreaElement) {
  el.style.height = "auto";
  const max = Number.parseFloat(getComputedStyle(el).maxHeight) || 160;
  el.style.height = `${Math.min(el.scrollHeight, max)}px`;
  // 留 1px 容差，避免小数像素在刚好等于上限时闪出滚动条
  el.style.overflowY = el.scrollHeight > max + 1 ? "auto" : "hidden";
}

function autosize(event: Event) {
  const el = event.target as HTMLTextAreaElement | null;
  if (el) applySize(el);
}

function resizeComposer() {
  const el = composerRef.value;
  if (el) applySize(el);
}

/** 发送后清空并把高度收回单行 */
async function resetComposer() {
  input.value = "";
  await nextTick();
  const el = composerRef.value;
  if (el) applySize(el);
}

function refLabel(type: ConversationRef["type"]) {
  if (type === "diary") return "日记";
  if (type === "experience") return "经验";
  return "知识";
}

/** 经验展示一句话结论，日记和知识展示正文摘要 */
function refText(item: ConversationRef): string {
  const value = item.type === "experience" ? item.summary || item.excerpt : item.excerpt || item.summary;
  return value ?? "";
}

/** 引用抽屉：消息里只留一个「搜索到 N 条」的胶囊，点开才看清单，避免正文被压住 */
const refDrawer = ref<ConversationRef[]>([]);

function openRefDrawer(items: ConversationRef[]) {
  // 复制一份，补全时不会改到原始消息的 payload
  refDrawer.value = items.map((item) => ({ ...item }));
  void fillMissingRefs();
}

function closeRefDrawer() {
  refDrawer.value = [];
}

/**
 * 历史消息的 refs 只存了 type/id/title（那时的 payload 没有摘要字段），
 * 直接展示会空着。这里按 id 回查一次详情补上正文，旧消息也能看到内容。
 */
async function fillMissingRefs() {
  const pending = refDrawer.value.filter((item) => !item.excerpt && !item.summary);
  if (!pending.length) return;
  await Promise.all(
    pending.map(async (item) => {
      try {
        if (item.type === "diary") {
          const diary = await library.fetchDiary(String(item.id));
          item.excerpt = diary?.content ?? "";
          item.summary = diary?.summary ?? "";
        } else if (item.type === "experience") {
          const experience = await library.fetchExperience(String(item.id));
          item.excerpt = experience?.problem ?? "";
          item.summary = experience?.lesson ?? "";
        } else {
          const knowledge = await library.fetchKnowledgeItem(String(item.id));
          item.excerpt = knowledge?.content ?? "";
          item.summary = knowledge?.summary ?? "";
        }
      } catch {
        // 取不到就保持空，卡片只显示标题，不打断抽屉
      }
    }),
  );
}

function openRef(item: ConversationRef) {
  closeRefDrawer();
  if (item.type === "diary") router.push(`/diaries/${item.id}`);
  else if (item.type === "experience") router.push(`/experiences/${item.id}`);
  else router.push(`/knowledge/${item.id}`);
}

async function submitHome() {
  const text = input.value.trim();
  if (!text || store.sending) return;
  // createConversation 是一次网络请求，这期间 sending 还没被置位。
  // 先抢锁，避免在这个窗口里连点发出多次同样的请求。
  store.sending = true;
  await resetComposer();
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
  if (!text || store.sending) return;
  await resetComposer();
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
  window.addEventListener("resize", resizeComposer);
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

onBeforeUnmount(() => window.removeEventListener("resize", resizeComposer));
</script>

<style scoped>
/* 标题已上移到顶栏（AppTopbar），内容区从顶栏下沿直接开始 */
.thread-dock {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: calc(100vh - var(--topbar-h));
  margin: calc(var(--main-pad-y) * -1) calc(var(--main-pad-x) * -1) -56px;
  padding: 0 var(--main-pad-x) 12px;
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
  /* 与顶栏留出呼吸空间：首个气泡不贴着分隔线 */
  padding-top: 20px;
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
  overflow-y: hidden;
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

/* 引用胶囊：消息里只占一行，清单进抽屉 */
.ref-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  margin-bottom: 2px;
  padding: 2px 8px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: transparent;
  color: var(--mute);
  font-size: 11px;
  line-height: 1.6;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.ref-pill:hover {
  color: var(--ink);
  border-color: var(--line-1);
}

.ref-pill-ico {
  width: 11px;
  height: 11px;
  opacity: 0.7;
}

.ref-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.34);
}

.ref-drawer {
  display: flex;
  flex-direction: column;
  width: min(420px, 92vw);
  height: 100%;
  border-left: 1px solid var(--line-2);
  background: var(--paper);
  box-shadow: -12px 0 32px rgba(0, 0, 0, 0.18);
  animation: ref-slide 0.18s ease-out;
}

@keyframes ref-slide {
  from {
    transform: translateX(24px);
    opacity: 0.6;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

.ref-drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line-2);
}

.ref-close {
  border: 0;
  background: transparent;
  color: var(--mute);
  font-size: 14px;
  cursor: pointer;
}

.ref-close:hover {
  color: var(--ink);
}

.ref-drawer-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 0;
}

/* 列表式：行与行用细分割线，不再用浮动卡片，避免每条都像一块独立面板 */
.ref-card {
  display: block;
  width: 100%;
  padding: 12px 18px;
  border: 0;
  border-bottom: 1px solid var(--line-2);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.ref-card:last-child {
  border-bottom: 0;
}

.ref-card:hover {
  background: var(--hover);
}

/* 徽章在上、标题在下，左边缘对齐 */
.ref-card-head {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.ref-kind {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--hover);
  color: var(--mute);
  font-size: 10px;
  line-height: 1.7;
}

.ref-title {
  max-width: 100%;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ref-excerpt {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 5px 0 0;
  color: var(--mute);
  font-size: 11px;
  line-height: 1.65;
}
</style>
