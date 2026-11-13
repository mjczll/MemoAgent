<template>
  <div
    v-if="isThread"
    class="fade-in interview-page thread-dock"
    :class="{ 'has-refs': refDrawer.length }"
  >
    <div class="interview-main">
      <!-- 滚动区与右侧刻度轨道同属一个定位容器，刻度才不会跟着内容滚动 -->
      <div class="chat-wrap">
        <div ref="scrollRef" class="chat-scroll" @scroll="updateScrollState">
        <template v-for="message in messages" :key="message.id">
          <div v-if="editingId === message.id" class="edit-box">
            <textarea
              v-model="editingText"
              rows="2"
              @keydown.esc="cancelEdit"
              @keydown.enter.exact.prevent="submitEdit(message)"
            />
            <div class="edit-box-actions">
              <button type="button" class="btn sm" @click="cancelEdit">取消</button>
              <button
                type="button"
                class="btn sm primary"
                :disabled="!editingText.trim() || store.sending"
                @click="submitEdit(message)"
              >
                发送
              </button>
            </div>
          </div>

          <ChatMessage
            v-else
            :message="message"
            :show-who="Boolean(message.payload?.supplement)"
            :who-label="message.payload?.supplement ? '模型补充' : 'MemoAgent'"
            @copy="copyMessage(message)"
            @edit="startEdit(message)"
            @regenerate="regenerate(message)"
          >
            <template #above>
              <button
                v-if="message.refs.length"
                type="button"
                class="ref-pill"
                :class="{ 'is-on': refSourceId === message.id }"
                :aria-label="`查看 ${message.refs.length} 条引用`"
                @click="openRefDrawer(message.id, message.refs)"
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

            <!-- 清单覆盖小字：只在模型调用过 listAssets 的回复下出现 -->
            <div v-if="message.payload?.listingHits != null" class="listing-note">
              命中 {{ message.payload.listingHits }} 条 · 列出 {{ message.payload.listingListed }} 条
            </div>
          </ChatMessage>
        </template>

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

        <!-- 刻度导航：附着在滚动区右缘，悬停鱼眼展开 + 预览 + 点击跳转 -->
        <MessageRail
          v-if="!refDrawer.length"
          :items="navItems"
          :active-id="activeNavId"
          @jump="jumpToMessage"
        />
      </div>

      <div class="composer">
        <button
          v-if="showToBottom"
          type="button"
          class="scroll-bottom"
          title="回到底部"
          aria-label="回到底部"
          @click="scrollToBottom"
        >
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M8 3.4v8.4" />
            <path d="M4.4 8.4 8 12l3.6-3.6" />
          </svg>
        </button>
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

    <!-- 引用面板：与对话并排，开着也能继续聊 -->
    <aside v-if="refDrawer.length" class="ref-panel" aria-label="引用">
      <header class="ref-panel-head">
        <b>搜索结果</b>
        <button type="button" class="ref-close" aria-label="关闭" @click="closeRefDrawer">✕</button>
      </header>
      <div class="ref-panel-body">
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
          :disabled="!input.trim() || starting"
          title="发送"
          aria-label="发送"
          @click="submitHome()"
        >
          ↑
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { errorMessage } from "@/api/http";
import { isAbortError } from "@/api/conversation";
import type { ConversationRef } from "@/api/conversation";
import ChatMessage from "@/components/chat/ChatMessage.vue";
import MessageRail from "@/components/chat/MessageRail.vue";
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
/** 首页创建对话的防连点锁，和「当前会话是否在输出」无关 */
const starting = ref(false);
const composerRef = ref<HTMLTextAreaElement | null>(null);
const drafting = ref(false);
const scrollRef = ref<HTMLElement | null>(null);

/** 往上翻了就浮出「回到底部」按钮，回到底部自动收起 */
const showToBottom = ref(false);

/** 问题定位：把用户的每句提问排成右侧一列，点一下就跳过去 */
const navItems = computed(() =>
  (store.current?.messages ?? [])
    .filter((message) => message.role === "user" && message.text?.trim())
    .map((message) => ({ id: message.id, text: message.text.trim() })),
);
const activeNavId = ref<number | null>(null);

function updateScrollState() {
  const el = scrollRef.value;
  if (!el) return;
  showToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight > 120;
  updateActiveNav();
}

/** 当前读到哪一条：最后一个顶部已经划到栏顶附近（含）的提问 */
function updateActiveNav() {
  const el = scrollRef.value;
  if (!el || !navItems.value.length) {
    activeNavId.value = null;
    return;
  }
  const top = el.getBoundingClientRect().top;
  let active = navItems.value[0].id;
  for (const item of navItems.value) {
    const node = el.querySelector<HTMLElement>(`[data-msg-id="${item.id}"]`);
    if (node && node.getBoundingClientRect().top - top <= 72) {
      active = item.id;
    }
  }
  activeNavId.value = active;
}

function jumpToMessage(id: number) {
  const el = scrollRef.value;
  const node = el?.querySelector<HTMLElement>(`[data-msg-id="${id}"]`);
  if (!el || !node) return;
  const delta = node.getBoundingClientRect().top - el.getBoundingClientRect().top;
  el.scrollTo({ top: el.scrollTop + delta - 16, behavior: "smooth" });
}

function scrollToBottom() {
  const el = scrollRef.value;
  if (!el) return;
  el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
  showToBottom.value = false;
}

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

/** 引用面板：与对话并排展示，消息里只留一个「搜索到 N 条」的胶囊 */
const refDrawer = ref<ConversationRef[]>([]);
const refSourceId = ref<number | null>(null);

function openRefDrawer(messageId: number, items: ConversationRef[]) {
  // 再点同一条 = 收起
  if (refSourceId.value === messageId) {
    closeRefDrawer();
    return;
  }
  refSourceId.value = messageId;
  // 复制一份，补全时不会改到原始消息的 payload
  refDrawer.value = items.map((item) => ({ ...item }));
  void fillMissingRefs();
}

function closeRefDrawer() {
  refSourceId.value = null;
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

/* ---------- 消息操作：复制 / 重新生成 / 编辑重发 ---------- */

const editingId = ref<number | null>(null);
const editingText = ref("");

async function copyMessage(message: { text: string }) {
  const text = (message.text ?? "").trim();
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ui.toast("已复制");
  } catch {
    ui.toast("复制失败，请手动选中文本", "error");
  }
}

async function regenerate(message: { id: number }) {
  const id = store.current?.id;
  if (!id || store.sending) return;
  try {
    await store.regenerate(id, message.id);
  } catch (error) {
    ui.toast(errorMessage(error, "重新生成失败"), "error");
  }
}

function startEdit(message: { id: number; text: string }) {
  editingId.value = message.id;
  editingText.value = message.text;
  void nextTick(() => {
    const box = document.querySelector<HTMLTextAreaElement>(".edit-box textarea");
    box?.focus();
    box?.select();
  });
}

function cancelEdit() {
  editingId.value = null;
  editingText.value = "";
}

async function submitEdit(message: { id: number }) {
  const id = store.current?.id;
  const text = editingText.value.trim();
  if (!id || !text || store.sending) return;
  editingId.value = null;
  editingText.value = "";
  try {
    await store.editAndResend(id, message.id, text);
  } catch (error) {
    ui.toast(errorMessage(error, "重新发送失败"), "error");
  }
}

async function submitHome() {
  const text = input.value.trim();
  // 首页不拦截「另一条对话正在输出」：这里只为创建请求本身防连点
  if (!text || starting.value) return;
  starting.value = true;
  await resetComposer();
  let id: number;
  try {
    id = await store.openFromHome(text);
  } catch (error) {
    starting.value = false;
    if (!isAbortError(error)) {
      ui.toast(errorMessage(error, "发送失败"), "error");
    }
    return;
  }
  // 创建完成就解锁：首页和对话页是同一个组件，路由切换不会重置状态，
  // 若把锁挂到整段回复结束，回到首页时按钮会一直停在禁用/停止态
  starting.value = false;
  await router.push(`/c/${id}`);
  void store.deliver(id, text).catch((error) => {
    if (!isAbortError(error)) {
      ui.toast(errorMessage(error, "发送失败"), "error");
    }
  });
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

// 切换会话时复位：否则上一条对话里滚上去的状态会让新对话停在顶部
watch(
  () => store.current?.id,
  () => {
    showToBottom.value = false;
  },
);

watch(
  () => (store.current?.messages ?? []).map((item) => item.text).join("\n").length,
  async () => {
    await nextTick();
    const el = scrollRef.value;
    if (!el) return;
    // 用户正往上翻看历史时不要抢滚动位置，交给「回到底部」按钮
    if (!showToBottom.value) el.scrollTop = el.scrollHeight;
    updateScrollState();
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
/* 清单覆盖小字：虚线与正文隔开，字号比 meta 更小一级 */
.listing-note {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--line);
  color: var(--faint);
  font-size: 11.5px;
}

/* 标题已上移到顶栏（AppTopbar），内容区从顶栏下沿直接开始 */
.thread-dock {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: calc(100vh - var(--topbar-h));
  margin: calc(var(--main-pad-y) * -1) calc(var(--main-pad-x) * -1) -56px;
  /* 右侧不留内边距：滚动条贴着窗口右边线；消息列自己居中，不受影响 */
  padding: 0 0 12px var(--main-pad-x);
}

.thread-dock .interview-main {
  position: relative;
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
  /* 默认 thin 约 8px。全局 * 设了 scrollbar-color，会让 ::-webkit-* 失效，
     这里两个都重置成 auto，让下面的伪元素规则接管宽度 */
  scrollbar-width: auto;
  scrollbar-color: auto;
}

.thread-dock .chat-scroll::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

.thread-dock .chat-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.thread-dock .chat-scroll::-webkit-scrollbar-thumb {
  border-radius: 2px;
  background: var(--line-2);
}

.thread-dock .chat-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--mute);
}

/* 消息列与输入框同宽并居中：长回复不再横跨整屏，读起来更聚焦 */
.thread-dock .chat-scroll > * {
  width: 100%;
  max-width: 720px;
  margin-left: auto;
  margin-right: auto;
}

/* 编辑用户消息：原地展开成输入框，取消 / 发送靠右下 */
.edit-box {
  padding: 10px 12px 8px;
  border: 1px solid var(--line-1);
  border-radius: 12px;
  background: var(--surface);
}

.edit-box textarea {
  width: 100%;
  min-height: 44px;
  max-height: 240px;
  padding: 0;
  border: 0;
  outline: none;
  resize: vertical;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  line-height: 1.7;
}

.edit-box-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
}

.thread-dock .composer {
  /* 相对定位只为「回到底部」按钮做锚点，按钮浮在输入框上方 */
  position: relative;
  flex: none;
  padding-top: 8px;
  background: linear-gradient(180deg, transparent, var(--paper) 36%);
}

/* 回到底部：贴在输入框上沿，右边缘与消息列对齐 */
.scroll-bottom {
  position: absolute;
  right: max(0px, calc((100% - 720px) / 2));
  bottom: calc(100% + 8px);
  z-index: 3;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  background: var(--surface);
  color: var(--ink-2);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

/* 悬停用实色：--hover 是半透明叠加，会把按钮底透出来，看起来像变透明了 */
.scroll-bottom:hover {
  background: var(--surface-2);
  border-color: var(--line-1);
  color: var(--ink);
}

.scroll-bottom:active {
  background: var(--surface-2);
}

.scroll-bottom svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 滚动区 + 刻度轨道：轨道绝对定位在这个容器里，不随内容滚动 */
.thread-dock .chat-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
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

  /* 窄屏放不下并排：引用面板改为右侧浮层，对话不被压扁 */
  .thread-dock.has-refs {
    display: flex;
    flex-direction: column;
  }

  .ref-panel {
    position: fixed;
    inset: 0 0 0 auto;
    z-index: 120;
    width: min(340px, 92vw);
    border: 0;
    border-left: 1px solid var(--line-2);
    border-radius: 0;
    box-shadow: -12px 0 32px rgba(0, 0, 0, 0.18);
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

/* 当前正在查看的那条消息，胶囊高亮 */
.ref-pill.is-on {
  border-color: var(--line-1);
  background: var(--hover);
  color: var(--ink);
}

.ref-pill-ico {
  width: 11px;
  height: 11px;
  opacity: 0.7;
}

/* 引用面板打开时：对话列收窄到左侧，面板占右侧一列，两者同时可用 */
.thread-dock.has-refs {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  grid-template-rows: minmax(0, 1fr);
  column-gap: 16px;
  /* 面板贴住右侧边界，不再留 24px 的正文内边距 */
  padding-right: 0;
}

.thread-dock.has-refs > * {
  min-width: 0;
  min-height: 0;
}

/* 面板打开后对话列会变宽，气泡按阅读宽度收窄，避免一行太长 */
.thread-dock.has-refs :deep(.bubble) {
  max-width: min(760px, 82%);
}

.ref-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  background: var(--paper);
  animation: ref-slide 0.18s ease-out;
}

@keyframes ref-slide {
  from {
    transform: translateX(16px);
    opacity: 0.6;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

.ref-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-2);
  font-size: 13px;
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

.ref-panel-body {
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
