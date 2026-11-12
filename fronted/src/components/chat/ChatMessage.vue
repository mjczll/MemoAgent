<template>
  <div
    class="bubble-row"
    :class="{ user: message.role === 'user' }"
    :data-msg-id="message.id"
  >
    <div v-if="message.role !== 'user'" class="avatar">{{ avatar }}</div>
    <div class="stack bubble-stack">
      <div v-if="showWho" class="who small">{{ whoLabel }}</div>
      <!-- 气泡上方的附属信息，例如引用条数胶囊 -->
      <slot name="above" />
      <div class="bubble" :class="{ 'bubble--slim': slim }">
        <MarkdownView v-if="markdown && message.role !== 'user'" :text="message.text" />
        <template v-else>{{ message.text }}</template>
      </div>
      <slot />
      <div v-if="showActions" class="msg-actions">
        <button type="button" class="msg-action" data-tip="复制" aria-label="复制" @click="emit('copy')">
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <rect x="5.6" y="5.6" width="7.9" height="8.9" rx="1.6" />
            <path d="M10.4 5.6V4a1.5 1.5 0 0 0-1.5-1.5H4A1.5 1.5 0 0 0 2.5 4v6A1.5 1.5 0 0 0 4 11.5h1.6" />
          </svg>
        </button>
        <button
          v-if="message.role === 'user'"
          type="button"
          class="msg-action"
          data-tip="修改"
          aria-label="修改"
          @click="emit('edit')"
        >
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2.5 13.5l.6-2.4 7.3-7.3 1.8 1.8-7.3 7.3-2.4.6z" />
            <path d="M9.6 4.6l1.8 1.8" />
          </svg>
        </button>
        <button
          v-else
          type="button"
          class="msg-action"
          data-tip="重新生成"
          aria-label="重新生成"
          @click="emit('regenerate')"
        >
          <!-- 圆形箭头：弧线留缺口，箭头独立在缺口处，缩小后也不会糊成一团 -->
          <svg viewBox="0 0 24 24" style="stroke-width: 2" aria-hidden="true">
            <polyline points="23 4 23 10 17 10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownView from "@/components/base/MarkdownView.vue";

const props = withDefaults(
  defineProps<{
    message: { id?: string; role: "user" | "ai"; text: string };
    markdown?: boolean;
    showWho?: boolean;
    whoLabel?: string;
  }>(),
  { markdown: true, showWho: false },
);

const emit = defineEmits<{
  (e: "copy"): void;
  (e: "edit"): void;
  (e: "regenerate"): void;
}>();

const avatar = computed(() => (props.message.role === "user" ? "我" : "✦"));
const whoLabel = computed(
  () => props.whoLabel ?? (props.message.role === "user" ? "我" : "MemoAgent"),
);

/**
 * 单行短消息：气泡高度压到和头像一致（30px），一行字看起来才是齐的。
 * 多行正文、列表、标题仍走默认的 10px 上下留白。
 */
const slim = computed(() => {
  const text = (props.message.text ?? "").trim();
  if (!text || text.includes("\n") || text.length > 40) return false;
  // Markdown 的块级写法（标题、列表、引用）会撑高，不能按单行处理
  return !/^(#{1,6}\s|[-*+]\s|>\s|\d+\.\s)/.test(text);
});

/** 空消息（正在流式生成）不显示操作按钮 */
const showActions = computed(() => Boolean((props.message.text ?? "").trim()));
</script>