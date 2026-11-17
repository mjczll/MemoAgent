<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">设置</h1>
        <p class="lede" style="margin-bottom: 0">
          日记、经验、知识已接入后端；个人资料仍保存在浏览器本地。
        </p>
      </div>
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
            <div class="field">
              <label>每周回顾生成日</label>
              <select v-model.number="form.reviewDay" class="input">
                <option :value="0">周日</option>
                <option :value="1">周一</option>
                <option :value="2">周二</option>
                <option :value="3">周三</option>
                <option :value="4">周四</option>
                <option :value="5">周五</option>
                <option :value="6">周六</option>
              </select>
            </div>
            <div class="field">
              <label>每周回顾生成时间</label>
              <input v-model="form.reviewTime" class="input" type="time" />
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
              <span>每周生成一次回顾</span>
              <input v-model="form.weeklyReview" type="checkbox" />
            </label>
            <button class="btn primary" :disabled="profileSaving" @click="saveProfile">保存偏好</button>
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
            <button class="btn sm block" @click="resetSeed">重新加载资产</button>
            <button class="btn sm block danger" @click="clearAll">清空本地设置</button>
            <p class="small faint" style="margin: 0">
              「重新加载资产」会从后端再拉一遍日记 / 经验 / 知识。
              「清空本地设置」只清浏览器里的会话和资料，不会删除后端数据。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { getProfile, saveProfileApi } from "@/api/profile";
import { errorMessage } from "@/api/http";
import { useLibraryStore } from "@/stores/library";
import { useUiStore, type ProfileSettings } from "@/stores/ui";

const ui = useUiStore();
const library = useLibraryStore();

const stats = computed(() => library.stats);

const form = reactive<ProfileSettings>({ ...ui.profile });
const profileSaving = ref(false);

onMounted(async () => {
  await ui.loadProfile();
  Object.assign(form, ui.profile);
});

async function saveProfile() {
  const payload: ProfileSettings = {
    displayName: form.displayName.trim() || "未命名",
    signature: form.signature,
    goalWeekly: Number(form.goalWeekly) > 0 ? Number(form.goalWeekly) : 1,
    reminderTime: form.reminderTime,
    reviewDay: form.reviewDay,
    reviewTime: form.reviewTime,
    autoExtract: form.autoExtract,
    autoSuggestKnowledge: form.autoSuggestKnowledge,
    weeklyReview: form.weeklyReview,
  };
  profileSaving.value = true;
  try {
    await saveProfileApi(payload);
    ui.saveProfile(payload);
    ui.toast("设置已保存");
  } catch (error) {
    ui.toast(errorMessage(error, "设置保存失败"), "error");
  } finally {
    profileSaving.value = false;
  }
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
  await library.resetToSeed();
  ui.toast("已从后端重新加载日记 / 经验 / 知识");
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
