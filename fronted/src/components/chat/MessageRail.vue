<template>
  <div
    v-if="visible"
    class="msg-rail"
    :class="{ 'is-open': hovering }"
  >
    <!-- 单一列表：行与行、刻度与刻度从头到尾都是同一批元素，
         悬停只改变容器宽度和文字可见性，刻度线本身不移动也不缩放 -->
    <div class="msg-rail__list" @mouseenter="hovering = true" @mouseleave="leave">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="msg-rail__row"
        :class="{ 'is-active': item.id === currentId }"
        :aria-label="item.text"
        @click="jump(item.id)"
      >
        <span class="msg-rail__text">{{ item.text }}</span>
        <span class="msg-rail__dash" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";

export interface RailItem {
  id: number;
  text: string;
}

const props = defineProps<{
  items: RailItem[];
  /** 当前阅读位置对应的条目 id（滚动时由父组件更新） */
  activeId: number | null;
}>();

const emit = defineEmits<{
  (e: "jump", id: number): void;
}>();

const hovering = ref(false);

/** 刚点过的那条：立即高亮，不必等滚动停下再靠 activeId 判定 */
const pickedId = ref<number | null>(null);

/** 当前高亮项：优先点选，其次滚动位置 */
const currentId = computed(() => pickedId.value ?? props.activeId);

/** 少于 2 轮不显示 */
const visible = computed(() => props.items.length > 1);

function jump(id: number) {
  pickedId.value = id;
  emit("jump", id);
}

/** 离开刻度栏只收起面板，点选的高亮保留 */
function leave() {
  hovering.value = false;
}

// 一滚动就交还给「当前位置」：离开后仍亮着点选那条，
// 但只要开始滚动，高亮就跟着视口走到哪亮到哪
watch(
  () => props.activeId,
  () => {
    pickedId.value = null;
  },
);
</script>

<style scoped>
/*
 * 容器右缘固定，悬停时向左变宽（right 不变、宽度变大），
 * 因此刻度线所在的那条竖线在两种状态下完全重合。
 */
.msg-rail {
  --rail-active: var(--ink);
  position: absolute;
  top: 12px;
  bottom: 12px;
  right: 9px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  /* 6 + 12 + 6 + 边框 2 = 26，正好容下收起态的刻度线 */
  width: 26px;
  /* 容器本身不接收鼠标：只有刻度簇那一块是命中区，
     右侧整条竖带的空白处不会再把面板叫出来，点击也直接穿透到消息区 */
  pointer-events: none;
  /* 不做宽度过渡：逐帧 reflow 会让文字被压缩抖动，直接切换更干净 */
}

.msg-rail.is-open {
  width: 360px;
}

/* 收起态也有同样的内边距与描边（透明），保证展开时刻度线不位移 */
.msg-rail__list {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 100%;
  overflow-y: auto;
  padding: 8px 0;
  border: 1px solid transparent;
  border-radius: 12px;
  scrollbar-width: thin;
  /* 只有刻度簇本身接收鼠标 */
  pointer-events: auto;
}

.msg-rail.is-open .msg-rail__list {
  border-color: var(--line-2);
  background: var(--surface);
  box-shadow: var(--shadow);
}

/* 行高恒定：收起态与展开态刻度间距一致 */
.msg-rail__row {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 32px;
  padding: 0 6px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
}

/* 收起态文字不参与布局，行内只剩刻度那 12px，整列收成一条竖线 */
.msg-rail__text {
  display: none;
  min-width: 0;
  overflow: hidden;
  color: var(--mute);
  /* 卡片放大、字号收小：文字只做索引，不需要和正文字号一致 */
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  text-align: left;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s;
}

.msg-rail.is-open .msg-rail__text {
  display: block;
  flex: 1 1 auto;
}

.msg-rail.is-open .msg-rail__row:hover .msg-rail__text {
  color: var(--ink);
}

/* 刻度线：尺寸、位置在两种状态下完全一致，只有颜色变化 */
.msg-rail__dash {
  flex: 0 0 auto;
  width: 12px;
  height: 2px;
  border-radius: 1px;
  background: var(--line-1);
  transition: background 0.15s;
}

.msg-rail.is-open .msg-rail__row:hover .msg-rail__dash {
  background: var(--ink);
}

.msg-rail__row.is-active .msg-rail__dash {
  background: var(--rail-active);
}

.msg-rail__row.is-active .msg-rail__text {
  color: var(--rail-active);
}

/* 窄屏 / 触屏没有悬停语义，整块隐藏 */
@media (max-width: 768px) {
  .msg-rail {
    display: none;
  }
}
</style>
