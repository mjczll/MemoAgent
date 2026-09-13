<template>
  <div class="bubble-row" :class="{ user: message.role === 'user' }">
    <div class="avatar">{{ message.role === 'user' ? '我' : '✦' }}</div>

    <div v-if="message.role === 'user'" class="bubble">
      {{ message.text }}
    </div>

    <div v-else class="stack" style="gap: 8px; max-width: 100%; min-width: 0">
      <details v-if="message.trace?.length" class="fold">
        <summary>推理与检索过程 · {{ message.trace.length }} 步</summary>
        <div class="fold-body">
          <ol style="margin: 0; padding-left: 18px">
            <li v-for="(step, index) in message.trace" :key="index">{{ step }}</li>
          </ol>
        </div>
      </details>

      <div v-if="message.pending" class="bubble typing">
        <i /><i /><i />
        <span class="faint small">正在翻你的日记、经验与知识库…</span>
      </div>

      <div v-else class="bubble">
        <MarkdownView :text="message.text" />
      </div>

      <div v-if="message.refs?.length && !message.pending" class="card flat">
        <div class="row between" style="margin-bottom: 8px">
          <b class="small">引用来源 · {{ message.refs.length }}</b>
          <span class="small faint">全部来自你自己的知识资产</span>
        </div>
        <RefList :items="message.refs" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AgentMessage } from "@/types";
import MarkdownView from "@/components/base/MarkdownView.vue";
import RefList from "@/components/base/RefList.vue";

defineProps<{ message: AgentMessage }>();
</script>
