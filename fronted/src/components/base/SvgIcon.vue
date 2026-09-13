<script setup lang="ts">
import { computed } from "vue";

/**
 * 极简 inline-SVG 图标：所有图标统一 16×16，线宽 1.4，圆头圆角，
 * 通过 currentColor 跟随文本色。设计风格与 Arena / DealEats 一致。
 */
type IconName =
  | "home"
  | "interview"
  | "agent"
  | "diary"
  | "experience"
  | "knowledge"
  | "graph"
  | "settings"
  | "rail-toggle"
  | "rail-collapse"
  | "rail-expand";

const props = withDefaults(
  defineProps<{ name: IconName; size?: number | string }>(),
  { size: 16 },
);

const px = computed(() =>
  typeof props.size === "number" ? `${props.size}px` : props.size,
);
</script>

<template>
  <svg
    class="svg-icon"
    :width="px"
    :height="px"
    viewBox="0 0 16 16"
    aria-hidden="true"
    focusable="false"
  >
    <!-- home：屋顶 + 屋身 -->
    <g v-if="name === 'home'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M2.5 7.2 8 2.8l5.5 4.4" />
      <path d="M3.6 6.8v6.4a.6.6 0 0 0 .6.6h2.4V10h2.8v3.8h2.4a.6.6 0 0 0 .6-.6V6.8" />
    </g>

    <!-- interview：对话气泡 + 麦克风 -->
    <g v-else-if="name === 'interview'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M2.5 3.4h8.4a1.2 1.2 0 0 1 1.2 1.2v4.6a1.2 1.2 0 0 1-1.2 1.2H6.6L4.2 12.6V10.4H2.5A1.2 1.2 0 0 1 1.3 9.2V4.6a1.2 1.2 0 0 1 1.2-1.2Z" />
      <path d="M5.7 6.6h.01M7.4 6.6h.01M9.1 6.6h.01" />
    </g>

    <!-- agent：星星 + 光晕 -->
    <g v-else-if="name === 'agent'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="m8 2 1.7 3.5 3.8.5-2.8 2.7.7 3.8L8 10.9 4.6 12.5l.7-3.8L2.5 6l3.8-.5L8 2Z" />
    </g>

    <!-- diary：本子 + 横线 -->
    <g v-else-if="name === 'diary'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.4 2.2h6.6l2.6 2.6v8.6a.8.8 0 0 1-.8.8H3.4a.8.8 0 0 1-.8-.8V3a.8.8 0 0 1 .8-.8Z" />
      <path d="M9.8 2.2v2.8h2.8" />
      <path d="M4.6 8.4h6.4M4.6 10.6h4.6" />
    </g>

    <!-- experience：奖章 + 飘带 -->
    <g v-else-if="name === 'experience'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="8" cy="6.6" r="2.8" />
      <path d="M5.6 8.6 4.2 13.4l3-1.6L8 13l.8-1.2 3 1.6-1.4-4.8" />
    </g>

    <!-- knowledge：书 -->
    <g v-else-if="name === 'knowledge'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3 3.2h5a1.6 1.6 0 0 1 1.6 1.6v8.4a1.6 1.6 0 0 0-1.6-1.6H3V3.2Z" />
      <path d="M13 3.2H8a1.6 1.6 0 0 0-1.6 1.6v8.4a1.6 1.6 0 0 1 1.6-1.6H13V3.2Z" />
    </g>

    <!-- graph：节点 + 连线 -->
    <g v-else-if="name === 'graph'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="3.6" cy="4.2" r="1.4" />
      <circle cx="12.2" cy="4.2" r="1.4" />
      <circle cx="8" cy="11.6" r="1.4" />
      <path d="M4.7 5 7 10.6M11.1 5 8.8 10.6" />
    </g>

    <!-- settings：经典齿轮（8 个梯形齿 + 中心孔） -->
    <g v-else-if="name === 'settings'" fill="currentColor">
      <!-- 8 个梯形齿：以中心 (8,8) 为基准，等距 45° -->
      <g transform="translate(8 8)">
        <!-- 每个齿是一个梯形，朝外辐射 2.2px -->
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(45)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(90)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(135)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(180)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(225)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(270)" />
        <path d="M-0.7 -7.6 h1.4 l0.4 1.8 h-2.2 z" transform="rotate(315)" />
        <!-- 齿轮主体圆 -->
        <circle cx="0" cy="0" r="5.4" />
      </g>
      <!-- 中心镂空孔 -->
      <circle cx="8" cy="8" r="2.4" fill="var(--paper)" />
    </g>

    <!-- rail-toggle：雪佛龙（展开/折叠通用） -->
    <g v-else-if="name === 'rail-toggle'" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="m4 6 4 4 4-4" />
    </g>

    <!-- rail-collapse：贴在侧栏右缘的折叠按钮：chevron-left（展开态下折叠） -->
    <g v-else-if="name === 'rail-collapse'" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="m9 4-4 4 4 4" />
    </g>

    <!-- rail-expand：chevron-right（折叠态下展开） -->
    <g v-else-if="name === 'rail-expand'" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="m7 4 4 4-4 4" />
    </g>
  </svg>
</template>

<style scoped>
.svg-icon {
  display: inline-block;
  vertical-align: middle;
  flex: 0 0 auto;
}
</style>
