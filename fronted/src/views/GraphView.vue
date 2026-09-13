<template>
  <div class="graph-page">
    <div ref="canvasHost" class="canvas-area" />

    <!-- 齿轮按钮 -->
    <button class="gear-btn" :class="{ on: settingsOpen }" title="设置" @click="settingsOpen = !settingsOpen">⚙</button>

    <!-- 浮动设置面板 -->
    <transition name="panel">
      <div v-if="settingsOpen" class="settings-panel">
        <div class="panel-tabs">
          <button v-for="tab in tabs" :key="tab.key" class="tab-btn" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
        </div>
        <div class="panel-body">
          <!-- Filters -->
          <template v-if="activeTab === 'filters'">
            <div class="ps">
              <label class="ps-label">搜索节点</label>
              <input v-model="searchQuery" class="input ps-input" placeholder="按标题匹配…" type="search" />
              <p v-if="searchQuery" class="ps-hint">匹配 <b>{{ searchHits }}</b> 个节点</p>
            </div>
            <div class="ps">
              <label class="ps-label">节点类型</label>
              <div class="ps-radios">
                <label class="ps-radio"><input v-model="typeFilter" type="radio" value="all" /><span>全部</span><span class="ps-count">{{ graphStatsAll.nodes }}</span></label>
                <label class="ps-radio"><input v-model="typeFilter" type="radio" value="diary" /><span class="dot" style="background:#7d9bbf" /><span>日记</span><span class="ps-count">{{ graphStatsAll.byType.diary }}</span></label>
                <label class="ps-radio"><input v-model="typeFilter" type="radio" value="experience" /><span class="dot" style="background:#a89bbf" /><span>经验</span><span class="ps-count">{{ graphStatsAll.byType.experience }}</span></label>
                <label class="ps-radio"><input v-model="typeFilter" type="radio" value="knowledge" /><span class="dot" style="background:#83b3a4" /><span>知识</span><span class="ps-count">{{ graphStatsAll.byType.knowledge }}</span></label>
              </div>
            </div>
            <div class="ps">
              <label class="ps-label">关系类型</label>
              <div class="ps-checks">
                <label v-for="rel in edgeRelationTypes" :key="rel.value" class="ps-check">
                  <input type="checkbox" :value="rel.value" v-model="edgeFilter" />
                  <span>{{ rel.label }}</span>
                  <span class="ps-count">{{ rel.count }}</span>
                </label>
              </div>
            </div>
            <div class="ps">
              <label class="ps-label">领域</label>
              <div class="ps-list">
                <button class="ps-item" :class="{ on: !domain }" @click="domain = null">
                  <span class="dot" style="background:var(--ink)" /><span class="grow">全局</span>
                </button>
                <button v-for="item in domainOptions" :key="item.name" class="ps-item" :class="{ on: domain === item.name }" @click="domain = item.name">
                  <span class="dot" style="background:var(--mute)" /><span class="grow">{{ item.name }}</span><span class="ps-count">{{ item.count }}</span>
                </button>
              </div>
            </div>
            <div class="ps">
              <label class="ps-check"><input v-model="orphansOnly" type="checkbox" /><span>只显示孤立节点</span></label>
            </div>
          </template>

          <!-- Groups -->
          <template v-if="activeTab === 'groups'">
            <div class="ps">
              <label class="ps-check toggle-row"><input v-model="colorGroups" type="checkbox" /><span>启用 Color Groups</span></label>
              <p class="ps-hint">按节点类型低饱和着色</p>
            </div>
            <div class="ps">
              <label class="ps-label">图例</label>
              <div class="legend">
                <div class="legend-row"><span class="ldot" style="background:#7d9bbf" /><span>日记</span></div>
                <div class="legend-row"><span class="ldot" style="background:#a89bbf" /><span>经验</span></div>
                <div class="legend-row"><span class="ldot" style="background:#83b3a4" /><span>知识</span></div>
                <div class="legend-row"><span class="ldot" /><span>节点越大 = 连接越多</span></div>
              </div>
            </div>
          </template>

          <!-- Display -->
          <template v-if="activeTab === 'display'">
            <div class="ps"><label class="ps-check toggle-row"><input v-model="display.showLabels" type="checkbox" /><span>显示标签</span></label></div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">文本透明度</label><span class="range-val">{{ display.textOpacity.toFixed(2) }}</span></div>
              <input v-model.number="display.textOpacity" type="range" min="0" max="1" step="0.05" class="ps-range" />
            </div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">节点大小</label><span class="range-val">{{ display.nodeScale.toFixed(1) }}×</span></div>
              <input v-model.number="display.nodeScale" type="range" min="0.5" max="2" step="0.1" class="ps-range" />
            </div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">连线粗细</label><span class="range-val">{{ display.linkWidth.toFixed(1) }}</span></div>
              <input v-model.number="display.linkWidth" type="range" min="0.5" max="3" step="0.1" class="ps-range" />
            </div>
            <div class="ps">
              <button class="btn sm ghost full-w" @click="replayGrowth">▶ 播放生长动画</button>
            </div>
          </template>

          <!-- Forces -->
          <template v-if="activeTab === 'forces'">
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">图谱向心力</label><span class="range-val">{{ forces.center }}</span></div>
              <input v-model.number="forces.center" type="range" min="0" max="200" step="5" class="ps-range" />
            </div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">节点排斥力</label><span class="range-val">{{ forces.repulsion }}</span></div>
              <input v-model.number="forces.repulsion" type="range" min="0" max="50000" step="500" class="ps-range" />
            </div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">连接吸引力</label><span class="range-val">{{ forces.attraction }}</span></div>
              <input v-model.number="forces.attraction" type="range" min="0" max="200" step="5" class="ps-range" />
            </div>
            <div class="ps">
              <div class="range-row"><label class="ps-label grow">连线长度</label><span class="range-val">{{ forces.linkLength }}</span></div>
              <input v-model.number="forces.linkLength" type="range" min="30" max="250" step="5" class="ps-range" />
            </div>
          </template>
        </div>
      </div>
    </transition>

    <!-- 缩放按钮 -->
    <div class="zoom-float">
      <button class="zbtn" title="缩小" @click="zoomBy(0.85)">－</button>
      <button class="zbtn" title="放大" @click="zoomBy(1.18)">＋</button>
      <button class="zbtn" title="适配全图" @click="() => fitView()">⊡</button>
      <button class="zbtn" title="重新中心" @click="recenter">↺</button>
    </div>

    <!-- 状态栏 -->
    <div class="status-pill">
      <span v-if="isLocal" class="local-tag">局部图</span>
      <span><b>{{ liveStats.nodes }}</b> 个节点 · <b>{{ liveStats.edges }}</b> 条连线</span>
      <template v-if="!isLocal">
        <span class="sep">·</span><span>日记 {{ graphStatsAll.byType.diary }}</span>
        <span class="sep">·</span><span>经验 {{ graphStatsAll.byType.experience }}</span>
        <span class="sep">·</span><span>知识 {{ graphStatsAll.byType.knowledge }}</span>
      </template>
      <template v-if="isLocal">
        <span class="sep">·</span>
        <label class="depth-label">
          深度
          <select v-model.number="localDepth" class="depth-select" @change="reloadLocal">
            <option :value="1">1</option>
            <option :value="2">2</option>
            <option :value="3">3</option>
          </select>
        </label>
      </template>
    </div>

    <!-- 选中节点信息卡 -->
    <transition name="card">
      <div v-if="selectedNode" class="node-card">
        <div class="card-head">
          <span class="card-kind" :class="`kind-${selectedNode.type}`">{{ typeLabel[selectedNode.type] }}</span>
          <span class="card-domain faint">{{ selectedNode.domain }}</span>
        </div>
        <div class="card-title">{{ selectedNode.label }}</div>
        <div v-if="selectedNode.summary" class="card-summary">{{ selectedNode.summary }}</div>
        <div class="card-meta">
          <span><b>{{ selectedNode.degree }}</b> 个连接</span>
          <span v-if="selectedNode.updatedAt">· {{ relative(selectedNode.updatedAt) }} 更新</span>
        </div>
        <div class="card-actions">
          <button class="btn sm" @click="openNode(selectedNode)">查看详情</button>
          <button class="btn sm ghost" @click="select(null)">关闭</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useGraphEngine } from "@/composables/useGraphEngine";
import { buildGraph, buildLocalGraph, graphStats } from "@/lib/graph";
import { relative } from "@/lib/format";
import { DOMAINS } from "@/mock/domains";
import { useLibraryStore } from "@/stores/library";
import type { GraphNodeInput, GraphEdgeInput, ForcesConfig, DisplayConfig } from "@/graph/GraphEngine";

const route = useRoute();
const router = useRouter();
const library = useLibraryStore();

/* ---- 判断全局/局部 ---- */
const isLocal = computed(() => route.name === "graph-local");
const localId = computed(() => (route.params.id as string) ?? "");
const localDepth = ref(Number(route.query.depth) ?? 2);

/* ---- UI state ---- */
const settingsOpen = ref(false);
const activeTab = ref<"filters" | "groups" | "display" | "forces">("filters");
const searchQuery = ref("");
const typeFilter = ref<"all" | "diary" | "experience" | "knowledge">("all");
const orphansOnly = ref(false);
const colorGroups = ref(false);
const domain = ref<string | null>(null);
const edgeFilter = ref<string[]>([]);

/* ---- Forces & Display ---- */
const forces = reactive<ForcesConfig>({ center: 40, repulsion: 30, attraction: 80, linkLength: 50 });
const display = reactive<DisplayConfig>({ showLabels: true, textOpacity: 0.5, nodeScale: 1, linkWidth: 1 });

/* ---- Engine ---- */
const canvasHost = ref<HTMLElement | null>(null);
const { liveStats, mount, setData, setForces, setDisplay, select, search, fitView, zoomBy, replayGrowth, getSelectedNode } = useGraphEngine();

/* ---- 数据 ---- */
const graphData = computed(() => {
  if (isLocal.value && localId.value) {
    return buildLocalGraph(library.data, localId.value, localDepth.value);
  }
  return buildGraph(library.data, domain.value);
});

const graphStatsAll = computed(() => graphStats(graphData.value));

const edgeRelationTypes = computed(() => {
  const counts: Record<string, number> = {};
  graphData.value.edges.forEach((e) => { counts[e.label] = (counts[e.label] ?? 0) + 1; });
  const labelMap: Record<string, string> = {
    提炼: "提炼（日记→经验）", 沉淀: "沉淀（经验→知识）",
    关联: "关联（日记→知识）", 相关: "相关（知识↔知识）", 归属: "归属（实体→领域）",
  };
  return Object.entries(counts).map(([value, count]) => ({ value, label: labelMap[value] ?? value, count })).sort((a, b) => b.count - a.count);
});

const domainOptions = computed(() => {
  const counts = library.domainCounts;
  return DOMAINS.filter((d) => (counts[d.name] ?? 0) > 0).map((d) => ({ name: d.name, count: counts[d.name] ?? 0 }));
});

const searchHits = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return 0;
  return graphData.value.nodes.filter((n) => n.label.toLowerCase().includes(q)).length;
});

/* ---- 辅助 ---- */
const tabs = [
  { key: "filters" as const, label: "Filters" },
  { key: "groups" as const, label: "Groups" },
  { key: "display" as const, label: "Display" },
  { key: "forces" as const, label: "Forces" },
];

function toInput(n: any, deg: number, colorGroupsOn: boolean): GraphNodeInput {
  const colorMap: Record<string, string> = { diary: "#7d9bbf", experience: "#a89bbf", knowledge: "#83b3a4", domain: "#cfcfcf" };
  return {
    id: n.id, label: n.label, type: n.type, domain: n.domain,
    degree: deg,
    size: 5 + Math.sqrt(deg) * 5,
    color: colorGroupsOn ? (colorMap[n.type] ?? "#8a8a8d") : "#8a8a8d",
    refId: n.refId,
    updatedAt: n.updatedAt,
  };
}

/* ---- 选中节点信息卡 ---- */
const typeLabel: Record<string, string> = { diary: "日记", experience: "经验", knowledge: "知识", domain: "领域" };

const selectedNode = computed(() => {
  const info = getSelectedNode();
  if (!info) return null;
  // 从 library 补充 summary
  let summary = info.summary;
  if (!summary && info.refId) {
    if (info.type === "diary") summary = library.diaryById(info.refId)?.summary;
    else if (info.type === "knowledge") summary = library.knowledgeById(info.refId)?.summary;
  }
  return { ...info, summary };
});

function openNode(n: { type: string; refId?: string }) {
  if (!n.refId) return;
  if (n.type === "diary") router.push(`/diaries/${n.refId}`);
  else if (n.type === "experience") router.push(`/experiences/${n.refId}`);
  else if (n.type === "knowledge") router.push(`/knowledge/${n.refId}`);
}

function buildInput(): { nodes: GraphNodeInput[]; edges: GraphEdgeInput[] } {
  const g = graphData.value;
  const degMap = new Map<string, number>();
  g.edges.forEach((e) => {
    degMap.set(e.source, (degMap.get(e.source) ?? 0) + 1);
    degMap.set(e.target, (degMap.get(e.target) ?? 0) + 1);
  });

  let nodes = g.nodes;
  const needle = searchQuery.value.trim().toLowerCase();
  if (typeFilter.value !== "all") nodes = nodes.filter((n) => n.type === typeFilter.value);
  if (needle) nodes = nodes.filter((n) => n.label.toLowerCase().includes(needle));
  if (orphansOnly.value) nodes = nodes.filter((n) => (degMap.get(n.id) ?? 0) <= 1);

  const nodeIds = new Set(nodes.map((n) => n.id));
  let edges = g.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target));
  if (edgeFilter.value.length > 0) edges = edges.filter((e) => edgeFilter.value.includes(e.label));

  return {
    nodes: nodes.map((n) => toInput(n, degMap.get(n.id) ?? 0, colorGroups.value)),
    edges: edges.map((e) => ({ id: e.id, source: e.source, target: e.target, rel: e.label })),
  };
}

function recenter() {
  select(null);
  fitView();
}

function reloadLocal() {
  const d = buildInput();
  setData(d.nodes, d.edges);
  setTimeout(() => fitView(), 300);
}

/* ---- Watches ---- */
watch(forces, (f) => setForces({ ...f }), { deep: true });
watch(display, (d) => setDisplay({ ...d }), { deep: true });
watch(colorGroups, () => { const d = buildInput(); setData(d.nodes, d.edges); setTimeout(() => fitView(), 300); });
watch([typeFilter, orphansOnly, edgeFilter], () => { const d = buildInput(); setData(d.nodes, d.edges); setTimeout(() => fitView(), 300); });
watch(searchQuery, (q) => search(q));
watch(graphData, () => { const d = buildInput(); setData(d.nodes, d.edges); setTimeout(() => fitView(), 300); });

/* ---- Mount ---- */
onMounted(async () => {
  if (!canvasHost.value) return;
  await mount(canvasHost.value);
  const d = buildInput();
  setData(d.nodes, d.edges);
  // 初始几次 fitView 让图居中
  fitView();
  setTimeout(() => fitView(), 500);
  setTimeout(() => fitView(), 1000);
});
</script>

<style scoped>
.graph-page { height: calc(100vh - 64px); margin: -14px -14px 0; position: relative; }
.canvas-area { position: relative; width: 100%; height: 100%; }

/* 齿轮 */
.gear-btn { position: absolute; top: 12px; right: 12px; z-index: 30; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border: 1px solid var(--line); border-radius: 5px; background: color-mix(in srgb, var(--paper) 88%, transparent); backdrop-filter: blur(8px); color: var(--mute); font-size: 16px; cursor: pointer; transition: color 0.18s, border-color 0.18s; }
.gear-btn:hover, .gear-btn.on { color: var(--ink); border-color: var(--ink-2); }

/* 设置面板 */
.settings-panel { position: absolute; top: 52px; right: 12px; z-index: 40; width: 300px; max-height: calc(100% - 72px); background: color-mix(in srgb, var(--paper) 92%, transparent); border: 1px solid var(--line); border-radius: 6px; backdrop-filter: blur(14px); display: flex; flex-direction: column; overflow: hidden; }
.panel-enter-active, .panel-leave-active { transition: opacity 0.16s ease-out, transform 0.16s ease-out; }
.panel-enter-from, .panel-leave-to { opacity: 0; transform: translateY(-6px); }
.panel-tabs { display: flex; border-bottom: 1px solid var(--line); padding: 0 4px; }
.tab-btn { flex: 1; padding: 8px 0; border: 0; background: transparent; color: var(--mute); font-size: 11px; font-weight: 500; letter-spacing: 0.04em; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; transition: color 0.18s; }
.tab-btn:hover { color: var(--ink); }
.tab-btn.active { color: var(--ink); border-bottom-color: var(--ink); }
.panel-body { padding: 10px 14px 14px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }

/* Panel section */
.ps { display: flex; flex-direction: column; gap: 5px; }
.ps-label { font-size: 10.5px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--faint); font-weight: 500; }
.ps-hint { margin: 0; font-size: 11px; color: var(--mute); }
.ps-hint b { color: var(--ink); font-weight: 500; }
.ps-input { font-size: 12.5px; padding: 5px 8px; }
.ps-radios, .ps-checks, .ps-list { display: flex; flex-direction: column; gap: 1px; }
.ps-radio, .ps-check { display: flex; align-items: center; gap: 6px; padding: 4px 6px; border-radius: 4px; font-size: 12.5px; color: var(--mute); cursor: pointer; transition: background 0.15s, color 0.15s; }
.ps-radio:hover, .ps-check:hover { background: var(--hover); color: var(--ink); }
.ps-radio:has(input:checked), .ps-check:has(input:checked) { color: var(--ink); }
.ps-radio input, .ps-check input { margin: 0; accent-color: var(--ink); }
.ps-count { margin-left: auto; font-family: var(--mono); font-size: 11px; }
.dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.ps-item { display: flex; align-items: center; gap: 6px; padding: 4px 6px; border: 0; border-radius: 4px; background: transparent; color: var(--mute); font-size: 12.5px; text-align: left; cursor: pointer; transition: background 0.15s, color 0.15s; }
.ps-item:hover { background: var(--hover); color: var(--ink); }
.ps-item.on { background: var(--hover-strong); color: var(--ink); }
.grow { flex: 1; min-width: 0; }
.toggle-row { padding: 6px; border-radius: 4px; }

/* Range */
.range-row { display: flex; align-items: center; gap: 6px; }
.range-val { font-family: var(--mono); font-size: 11px; color: var(--ink); min-width: 32px; text-align: right; }
.ps-range { -webkit-appearance: none; appearance: none; width: 100%; height: 3px; background: var(--line); border-radius: 2px; outline: none; }
.ps-range::-webkit-slider-thumb { -webkit-appearance: none; width: 12px; height: 12px; border-radius: 50%; background: var(--ink); cursor: pointer; border: 0; }
.ps-range::-moz-range-thumb { width: 12px; height: 12px; border-radius: 50%; background: var(--ink); cursor: pointer; border: 0; }

/* 图例 */
.legend { display: flex; flex-direction: column; gap: 4px; }
.legend-row { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--mute); }
.ldot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: var(--mute); }

/* 缩放 */
.zoom-float { position: absolute; bottom: 44px; right: 12px; z-index: 20; display: flex; flex-direction: column; gap: 2px; }
.zbtn { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border: 1px solid var(--line); border-radius: 4px; background: color-mix(in srgb, var(--paper) 88%, transparent); backdrop-filter: blur(8px); color: var(--mute); font-size: 14px; cursor: pointer; transition: color 0.15s, border-color 0.15s; }
.zbtn:hover { color: var(--ink); border-color: var(--ink-2); }

/* 状态栏 */
.status-pill { position: absolute; bottom: 10px; left: 50%; transform: translateX(-50%); z-index: 20; display: flex; align-items: center; gap: 6px; padding: 4px 14px; background: color-mix(in srgb, var(--paper) 85%, transparent); border: 1px solid var(--line); border-radius: 20px; backdrop-filter: blur(10px); font-size: 11px; font-family: var(--mono); color: var(--mute); white-space: nowrap; }
.status-pill b { color: var(--ink); font-weight: 500; }
.sep { opacity: 0.3; }
.local-tag { background: rgba(131,179,164,0.2); color: #a8d6c1; padding: 1px 6px; border-radius: 3px; font-size: 10px; font-weight: 500; }
.depth-label { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--mute); }
.depth-select { background: transparent; border: 1px solid var(--line); border-radius: 3px; color: var(--ink); font-size: 11px; padding: 1px 4px; font-family: var(--mono); }

.full-w { width: 100%; }

/* ============ 选中节点信息卡 ============ */
.node-card {
  position: absolute;
  top: 12px;
  right: 52px;
  width: 260px;
  padding: 14px 16px;
  background: color-mix(in srgb, var(--paper) 92%, transparent);
  border: 1px solid var(--line);
  border-radius: 6px;
  backdrop-filter: blur(14px);
  z-index: 25;
}
.card-head { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.card-kind {
  font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em;
  padding: 2px 6px; border-radius: 3px; font-weight: 500;
}
.kind-diary { background: rgba(125,155,191,0.18); color: #a8c0d8; }
.kind-experience { background: rgba(168,155,191,0.18); color: #c4b7d4; }
.kind-knowledge { background: rgba(131,179,164,0.18); color: #a8d6c1; }
.kind-domain { background: rgba(207,207,207,0.18); color: var(--ink); }
.card-domain { font-size: 11px; font-family: var(--mono); }
.card-title { font-size: 14px; font-weight: 600; color: var(--ink); margin-bottom: 6px; word-break: break-word; }
.card-summary { font-size: 12px; color: var(--mute); margin-bottom: 8px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.card-meta { display: flex; flex-wrap: wrap; gap: 4px; font-size: 11px; color: var(--mute); margin-bottom: 10px; }
.card-meta b { font-family: var(--mono); color: var(--ink); font-weight: 500; }
.card-actions { display: flex; gap: 6px; }
.card-actions .btn.sm { flex: 1; }
.card-enter-active, .card-leave-active { transition: opacity 0.18s ease-out, transform 0.18s ease-out; }
.card-enter-from, .card-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
