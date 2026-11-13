<template>
  <div class="filter-bar">
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * 筛选区容器：只负责布局（高度、间距、换行），不关心任何筛选状态。
 * 桌面端一行，放不下时换行；≤768px 时搜索框独占一行。
 */
</script>
