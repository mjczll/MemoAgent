<template>
  <div class="fade-in">
    <section class="hero">
      <h1>今天，<em>发生了什么？</em></h1>
      <p>
        和 AI 聊聊就好。MemoAgent 会用采访的方式听你讲完一件事，
        再把它整理成日记、提炼成经验、沉淀成知识。
      </p>

      <div class="ask-box">
        <textarea
          v-model="draft"
          class="textarea"
          rows="1"
          placeholder="给 MemoAgent 发送消息"
          @keydown.enter.exact.prevent="startInterview"
          @input="autosize"
          ref="textareaRef"
        />
        <div class="ask-actions">
          <span class="ask-tools">
            <span class="ask-hint">回车开始采访</span>
          </span>
          <button
            class="ask-go"
            type="button"
            :disabled="!draft.trim()"
            title="开始采访"
            aria-label="开始采访"
            @click="startInterview"
          >
            ↑
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const draft = ref("");
const textareaRef = ref<HTMLTextAreaElement | null>(null);

function autosize() {
  const el = textareaRef.value;
  if (!el) return;
  el.style.height = "auto";
  el.style.height = `${Math.min(el.scrollHeight, 180)}px`;
}

function startInterview() {
  const text = draft.value.trim();
  if (!text) return;
  sessionStorage.setItem("memoagent:opening", text);
  router.push("/interview");
}

onMounted(() => {
  const el = textareaRef.value;
  if (el) el.focus();
});
</script>