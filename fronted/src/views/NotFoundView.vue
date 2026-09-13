<template>
  <div class="fade-in">
    <EmptyState
      icon="🧭"
      title="这条路径还没有被织进网里"
      hint="页面可能已经改名，或者链接是自己手敲的。回到首页，或者让 Agent 帮你找。"
    >
      <div class="row" style="gap: 8px; justify-content: center">
        <button class="btn primary sm" @click="router.push('/')">回到首页</button>
        <button class="btn sm" @click="router.push('/agent')">问 Agent</button>
        <button class="btn sm" @click="router.push('/search')">搜索</button>
      </div>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import EmptyState from "@/components/base/EmptyState.vue";

const router = useRouter();
</script>
