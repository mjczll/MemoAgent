<template>
  <div class="fade-in">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">设置</h1>
        <p class="lede" style="margin-bottom: 0">
          日记、经验、知识与个人偏好均已接入后端；回顾排期到点后会出现在顶栏待办。
        </p>
      </div>
    </div>

    <div class="split left-wide">
      <div class="stack">
        <div class="panel">
          <div class="panel-head"><b>每周回顾</b></div>
          <div class="panel-body stack" style="gap: 12px">
            <label class="row between" style="cursor: pointer">
              <span>每周生成一次回顾</span>
              <input v-model="form.weeklyReview" type="checkbox" />
            </label>
            <template v-if="form.weeklyReview">
              <div class="row" style="gap: 12px">
                <div class="field grow">
                  <label>回顾生成日</label>
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
                <div class="field grow">
                  <label>生成时间</label>
                  <input v-model="form.reviewTime" class="input" type="time" />
                </div>
              </div>
              <p class="small faint" style="margin: 0">
                到点后不会自动生成：顶栏待办会出现「本周回顾待生成」，由你确认后一键生成回顾草稿。
              </p>
            </template>
            <button class="btn primary" :disabled="profileSaving" @click="saveProfile">保存偏好</button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><b>采访与沉淀</b></div>
          <div class="panel-body stack" style="gap: 10px">
            <label class="row between" style="cursor: pointer">
              <span>采访结束后自动结构化提炼（问题 / 原因 / 方案 / 经验）</span>
              <input v-model="form.autoExtract" type="checkbox" />
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

            <button class="btn sm block" :disabled="obsidianBusy" @click="exportObsidianZip">
              导出 Markdown 包（{{ obsidian.total }} 篇）
            </button>
            <button
              v-if="vaultState.found"
              class="btn primary sm block"
              :disabled="obsidianBusy"
              @click="writeToDetectedVault"
            >
              写入 Vault（{{ vaultState.name }}/MemoAgent）
            </button>
            <template v-else-if="!vaultDetecting">
              <p v-if="vaultState.message" class="small faint" style="margin: 0">
                {{ vaultState.message }}
              </p>
              <button class="btn sm block" :disabled="obsidianBusy" @click="detectVault">
                重新识别 Vault
              </button>
              <button
                v-if="canWriteDirect"
                class="btn sm block"
                :disabled="obsidianBusy"
                @click="writeToVault"
              >
                手动选择目录写入
              </button>
            </template>
            <p
              v-if="vaultState.found"
              class="small faint"
              style="margin: 0; word-break: break-all"
            >
              目标 Vault：{{ vaultState.path }}
            </p>
            <p v-if="obsidianError" class="small" style="margin: 0; color: var(--danger)">
              {{ obsidianError }}
            </p>

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
import { saveProfileApi } from "@/api/profile";
import { errorMessage } from "@/api/http";
import {
  downloadObsidianZip,
  fetchObsidianPreview,
  fetchObsidianVault,
  supportsDirectWrite,
  writeNotesToVault,
  writeObsidianToVault,
  type ObsidianPreview,
  type ObsidianVault,
} from "@/api/obsidian";
import { useLibraryStore } from "@/stores/library";
import { useUiStore, type ProfileSettings } from "@/stores/ui";

const ui = useUiStore();
const library = useLibraryStore();

const stats = computed(() => library.stats);

const form = reactive<ProfileSettings>({ ...ui.profile });
const profileSaving = ref(false);

const obsidian = ref<ObsidianPreview>({ diaries: 0, experiences: 0, knowledge: 0, total: 0 });
const obsidianBusy = ref(false);
const obsidianError = ref("");
const canWriteDirect = supportsDirectWrite();

/** 后端读 obsidian.json 得到的当前 Vault；识别失败时退回浏览器手选目录 */
const vaultState = ref<ObsidianVault>({
  found: false,
  path: null,
  name: null,
  configFile: "",
  message: "",
  candidates: [],
});
const vaultDetecting = ref(true);

onMounted(async () => {
  await ui.loadProfile();
  Object.assign(form, ui.profile);
  loadObsidian();
  detectVault();
});

async function loadObsidian() {
  try {
    obsidian.value = await fetchObsidianPreview();
    obsidianError.value = "";
  } catch (error) {
    obsidianError.value = errorMessage(error, "无法读取导出统计，请确认后端已重启");
  }
}

/** 后端读 obsidian.json，找出 Obsidian 当前打开的 Vault */
async function detectVault() {
  vaultDetecting.value = true;
  try {
    vaultState.value = await fetchObsidianVault();
  } catch (error) {
    vaultState.value = {
      found: false,
      path: null,
      name: null,
      configFile: "",
      message: errorMessage(error, "识别 Vault 失败，请确认后端已重启"),
      candidates: [],
    };
  } finally {
    vaultDetecting.value = false;
  }
}

/** 直接写盘：不需要选目录，路径用识别结果；内容没变的文件由后端跳过 */
async function writeToDetectedVault() {
  obsidianBusy.value = true;
  try {
    const result = await writeObsidianToVault(vaultState.value.path || undefined);
    if (result.failed > 0) {
      ui.toast(result.message || `${result.failed} 篇写入失败`, "error");
    } else if (result.written > 0) {
      ui.toast(`已写入 ${result.written} 篇，跳过 ${result.skipped} 篇未变化`);
    } else {
      ui.toast("内容没有变化，已跳过全部笔记");
    }
  } catch (error) {
    ui.toast(errorMessage(error, "写入 Vault 失败"), "error");
  } finally {
    obsidianBusy.value = false;
  }
}

async function exportObsidianZip() {
  obsidianBusy.value = true;
  try {
    const result = await downloadObsidianZip();
    ui.toast(`已导出 ${Math.max(1, Math.round(result.bytes / 1024))} KB`);
  } catch (error) {
    ui.toast(errorMessage(error, "导出失败"), "error");
  } finally {
    obsidianBusy.value = false;
  }
}

async function writeToVault() {
  obsidianBusy.value = true;
  try {
    const result = await writeNotesToVault();
    ui.toast(
      result.written > 0
        ? `已写入 ${result.written} 篇，跳过 ${result.skipped} 篇未变化`
        : "内容没有变化，已跳过全部笔记",
    );
  } catch (error) {
    // 用户在目录选择框里点了取消：DOMException / AbortError，不是失败
    if ((error as DOMException)?.name === "AbortError") return;
    ui.toast(errorMessage(error, "写入 Vault 失败"), "error");
  } finally {
    obsidianBusy.value = false;
  }
}

async function saveProfile() {
  const payload: ProfileSettings = {
    displayName: form.displayName.trim() || "未命名",
    signature: form.signature,
    reviewDay: form.reviewDay,
    reviewTime: form.reviewTime,
    autoExtract: form.autoExtract,
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
