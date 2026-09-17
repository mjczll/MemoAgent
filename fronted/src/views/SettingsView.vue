<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">设置</h1>
        <p class="lede" style="margin-bottom: 0">
          日记已接入后端；个人资料、经验、知识仍保存在浏览器本地。
        </p>
      </div>
      <span class="badge">日记已联调</span>
    </div>

    <div class="split left-wide">
      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>个人资料</b></div>
          <div class="panel-body stack" style="gap: 12px">
            <div class="field">
              <label>昵称</label>
              <input v-model="form.displayName" class="input" placeholder="你的昵称" />
            </div>
            <div class="field">
              <label>个人签名</label>
              <input v-model="form.signature" class="input" placeholder="一句话描述你的知识态度" />
            </div>
            <div class="row" style="gap: 12px">
              <div class="field grow">
                <label>每周目标（篇）</label>
                <input v-model.number="form.goalWeekly" class="input" type="number" min="1" />
              </div>
              <div class="field grow">
                <label>日记提醒时间</label>
                <input v-model="form.reminderTime" class="input" type="time" />
              </div>
            </div>
            <button class="btn primary" @click="saveProfile">保存资料</button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>采访与沉淀</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <label class="row between" style="cursor: pointer">
              <span>采访结束后自动结构化提炼（问题 / 原因 / 方案 / 经验）</span>
              <input v-model="form.autoExtract" type="checkbox" />
            </label>
            <label class="row between" style="cursor: pointer">
              <span>自动建议关联的知识条目</span>
              <input v-model="form.autoSuggestKnowledge" type="checkbox" />
            </label>
            <label class="row between" style="cursor: pointer">
              <span>每周生成一次回顾</span>
              <input v-model="form.weeklyReview" type="checkbox" />
            </label>
            <button class="btn primary" @click="saveProfile">保存偏好</button>
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>数据管理</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <div class="kv">
              <div class="kv-item">
                <span>日记</span>
                <p>{{ stats.diaries }} 篇</p>
              </div>
              <div class="kv-item">
                <span>经验</span>
                <p>{{ stats.experiences }} 条</p>
              </div>
              <div class="kv-item">
                <span>知识</span>
                <p>{{ stats.knowledge }} 条</p>
              </div>
            </div>

            <button class="btn sm block" @click="exportJson">导出全部数据（JSON）</button>
            <button class="btn sm block" @click="resetSeed">恢复示例数据</button>
            <button class="btn sm block danger" @click="clearAll">清空本地数据</button>
            <p class="small faint" style="margin: 0">
              「清空本地数据」只清浏览器里的经验 / 知识 / 会话 / 设置，不会删除后端日记。
              「恢复示例数据」同样只重置本地经验与知识。
            </p>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>关于这个原型</b></div>
          <div class="panel-body small" style="color: var(--mute)">
            <p style="margin: 0 0 8px">
              MemoAgent · AI 采访式个人经验与知识管理系统（前端高保真原型）。
            </p>
            <p style="margin: 0">
              技术栈：Vue 3 + TypeScript + Vite + Pinia + vue-router；图谱由内联 SVG 绘制；
              日记列表 / 详情 / 编辑已对接后端；采访、经验、知识、Agent 仍为本地 Mock。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from "vue";
import { useLibraryStore } from "@/stores/library";
import { useUiStore, type ProfileSettings } from "@/stores/ui";

const ui = useUiStore();
const library = useLibraryStore();

const stats = computed(() => library.stats);

const form = reactive<ProfileSettings>({ ...ui.profile });

function saveProfile() {
  ui.saveProfile({
    displayName: form.displayName.trim() || "未命名",
    signature: form.signature,
    goalWeekly: Number(form.goalWeekly) > 0 ? Number(form.goalWeekly) : 1,
    reminderTime: form.reminderTime,
    autoExtract: form.autoExtract,
    autoSuggestKnowledge: form.autoSuggestKnowledge,
    weeklyReview: form.weeklyReview,
  });
  ui.toast("设置已保存到本地");
}

function exportJson() {
  const payload = {
    exportedAt: new Date().toISOString(),
    profile: ui.profile,
    diaries: library.diaries,
    experiences: library.experiences,
    knowledge: library.knowledge,
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], {
    type: "application/json;charset=utf-8",
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `memoagent-export-${new Date().toISOString().slice(0, 10)}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
  ui.toast("已导出当前浏览器数据");
}

async function resetSeed() {
  if (!window.confirm("恢复示例数据会覆盖本地已有的经验 / 知识，日记仍以后端为准。确定继续吗？")) return;
  await library.resetToSeed();
  ui.toast("已恢复本地示例经验 / 知识");
}

function clearAll() {
  if (!window.confirm("确定清空浏览器中保存的全部原型数据吗？此操作不可撤销。")) return;
  Object.keys(localStorage)
    .filter((key) => key.startsWith("memoagent:"))
    .forEach((key) => localStorage.removeItem(key));
  ui.toast("本地数据已清空，正在重新载入…");
  window.setTimeout(() => window.location.reload(), 600);
}
</script>
