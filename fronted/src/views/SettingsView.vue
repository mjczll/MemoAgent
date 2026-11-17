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

            <!-- Obsidian：唯一入口。本地与云端都是「连一次，之后点一下就写进你电脑的 Vault」 -->
            <div class="stack" style="gap: 8px">
              <div class="row between">
                <b class="small">Obsidian</b>
                <span v-if="plugin.paired" class="small faint">
                  {{ plugin.vaultName }}{{ pluginOnline ? " · 在线" : " · 离线" }}
                </span>
              </div>

              <template v-if="plugin.paired">
                <button
                  class="btn primary sm block"
                  :disabled="importing || obsidianBusy"
                  @click="startPluginImport"
                >
                  {{ importButtonText }}
                </button>

                <div v-if="task" class="stack" style="gap: 6px">
                  <div
                    style="
                      height: 4px;
                      border-radius: 999px;
                      background: var(--line-2);
                      overflow: hidden;
                    "
                  >
                    <div
                      :style="{
                        width: progressPercent + '%',
                        height: '100%',
                        background: taskFailed ? 'var(--danger)' : 'var(--accent)',
                        transition: 'width .2s ease',
                      }"
                    />
                  </div>
                  <p class="small faint" style="margin: 0">{{ taskText }}</p>
                  <p
                    v-if="task.problems?.length"
                    class="small"
                    style="margin: 0; color: var(--danger); word-break: break-all"
                  >
                    首个问题：{{ task.problems[0]?.path
                    }}<template v-if="task.problems[0]?.error">（{{ task.problems[0]?.error }}）</template>
                  </p>
                </div>

                <p v-if="!pluginOnline && !importing" class="small faint" style="margin: 0">
                  Obsidian 已 {{ pluginIdleMinutes }} 分钟没有响应。打开它之后，未完成的任务会自动继续。
                </p>
                <p class="small faint" style="margin: 0">
                  写入 {{ plugin.vaultName }} 的 MemoAgent/ 目录。
                  <a href="#" style="color: var(--accent)" @click.prevent="connectObsidian">重新连接</a>
                </p>
              </template>

              <template v-else>
                <p class="small faint" style="margin: 0">
                  连一次，之后点一下就把日记、经验、知识写进你电脑上 Obsidian 的 MemoAgent/ 目录。
                  不用选目录，也不用记服务器地址。
                </p>
                <button class="btn primary sm block" :disabled="installing" @click="connectObsidian">
                  {{ installing ? "正在准备…" : "连接 Obsidian" }}
                </button>
              </template>
            </div>

            <details>
              <summary class="small faint" style="cursor: pointer">其他方式</summary>
              <div class="stack" style="gap: 8px; margin-top: 8px">
                <button class="btn sm block" :disabled="obsidianBusy" @click="exportObsidianZip">
                  导出 Markdown 包（{{ obsidian.total }} 篇）
                </button>
                <p class="small faint" style="margin: 0">
                  没装 Obsidian、或想自己决定放在哪里时用这个：下载 zip，解压到任意目录。
                </p>
                <p v-if="obsidianError" class="small" style="margin: 0; color: var(--danger)">
                  {{ obsidianError }}
                </p>
              </div>
            </details>

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

    <ModalDialog v-model="connectOpen" :title="connectTitle" width="520px">
      <div class="stack" style="gap: 12px">
        <!-- 同机：后端已经把插件和配置都写好了，只剩重载 -->
        <template v-if="connectStep === 'installed'">
          <p class="small" style="margin: 0">
            插件已写入 <b>{{ connectVaultName || "当前 Vault" }}</b>，连接配置也一并填好了。
          </p>
          <p class="small" style="margin: 0">
            现在只需重载 Obsidian：按 <b>Ctrl+P</b> 搜
            <b>Reload app without saving</b>，或直接重启它。
          </p>
          <button class="btn primary block" @click="openConnectedVault">打开 Obsidian</button>
          <p class="small faint" style="margin: 0">
            如果打开后提示「受限模式」，去 设置 → 第三方插件 关掉它，确认 MemoAgent 处于启用状态。
            之后回来点「一键导入 Obsidian」即可。
          </p>
        </template>

        <!-- 云端：服务器碰不到你的磁盘，插件得手动放一次 -->
        <template v-else-if="connectStep === 'manual'">
          <p class="small" style="margin: 0">1. 下载插件包，解压后得到一个 <b>memoagent</b> 文件夹。</p>
          <a class="btn sm block" :href="PLUGIN_PACKAGE_URL" download>下载插件包</a>
          <p class="small" style="margin: 0">
            2. 把它整个放进 <code>&lt;你的 Vault&gt;/.obsidian/plugins/</code>，重载 Obsidian，
            并在「第三方插件」里启用它。
          </p>
          <p class="small" style="margin: 0">3. 回来点下面的按钮，Obsidian 会自动连上。</p>
          <button class="btn primary block" :disabled="pairBusy" @click="startManualConnect">
            我已放好，继续
          </button>
        </template>

        <!-- 云端第 3 步：一条 obsidian:// 链接，点了就配对 -->
        <template v-else>
          <a class="btn primary block" :href="manualLink">在 Obsidian 中完成连接</a>
          <p class="small faint" style="margin: 0">
            浏览器会问「是否允许此网站打开 Obsidian」，选允许即可。链接{{ pairRemainText }}。
          </p>
          <details>
            <summary class="small faint" style="cursor: pointer">点了没反应？手动填码</summary>
            <div class="stack" style="gap: 8px; margin-top: 8px">
              <div
                style="
                  font-family: var(--mono);
                  font-size: 26px;
                  letter-spacing: 8px;
                  font-weight: 700;
                  text-align: center;
                  padding: 12px 0 12px 8px;
                  background: var(--surface-2);
                  border-radius: var(--radius);
                "
              >
                {{ pairCode || "……" }}
              </div>
              <p class="small faint" style="margin: 0">
                打开 Obsidian → 设置 → MemoAgent，把上面的码填进「连接状态」，点「连接」。
              </p>
              <button class="btn sm" :disabled="pairBusy" @click="refreshPairCode">换一个码</button>
            </div>
          </details>
        </template>
      </div>
      <template #foot>
        <button class="btn" @click="connectOpen = false">关闭</button>
      </template>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import { saveProfileApi } from "@/api/profile";
import { errorMessage } from "@/api/http";
import {
  downloadObsidianZip,
  fetchObsidianPreview,
  type ObsidianPreview,
} from "@/api/obsidian";
import { useLibraryStore } from "@/stores/library";
import { useUiStore, type ProfileSettings } from "@/stores/ui";
import {
  installObsidianPlugin,
  PLUGIN_PACKAGE_URL,
  pairLink,
  createImportTask,
  createPairCode,
  fetchPluginStatus,
  isFinished,
  subscribeImportTask,
  type ImportTask,
  type PluginStatus,
} from "@/api/obsidianImport";

const ui = useUiStore();
const library = useLibraryStore();

const stats = computed(() => library.stats);

const form = reactive<ProfileSettings>({ ...ui.profile });
const profileSaving = ref(false);

const obsidian = ref<ObsidianPreview>({ diaries: 0, experiences: 0, knowledge: 0, total: 0 });
const obsidianBusy = ref(false);
const obsidianError = ref("");

/* ---- Obsidian 接入：本地由后端直接装，云端走「下载包 + 一条链接」 ---- */

const plugin = ref<PluginStatus>({ paired: false });

const connectOpen = ref(false);
/** installed = 后端已写进本机 Vault；manual = 服务器碰不到你的磁盘，得手动放插件；link = 生成连接链接 */
const connectStep = ref<"installed" | "manual" | "link">("manual");
const connectVaultName = ref("");
const installing = ref(false);

const pairBusy = ref(false);
const pairCode = ref("");
const pairSecondsLeft = ref(0);
const importing = ref(false);
const task = ref<ImportTask | null>(null);

const connectTitle = computed(() =>
  connectStep.value === "installed" ? "插件已装好" : "连接 Obsidian",
);

/** obsidian:// 链接交给插件自己解析，用户不用手抄 6 位码 */
const manualLink = computed(() => (pairCode.value ? pairLink(pairCode.value) : "#"));

let pairPoll: number | undefined;
let pairTick: number | undefined;
let streamClose: (() => void) | null = null;
let offlineTimer: number | undefined;
let statusTimer: number | undefined;

/** 插件平时挂着长连接（20 秒一轮），超过 3 分钟没有任何心跳就认为 Obsidian 没开 */
const pluginOnline = computed(() => {
  if (!plugin.value.lastActiveAt) return false;
  return Date.now() - new Date(plugin.value.lastActiveAt).getTime() < 180_000;
});

const pluginIdleMinutes = computed(() => {
  if (!plugin.value.lastActiveAt) return 0;
  const diff = Date.now() - new Date(plugin.value.lastActiveAt).getTime();
  return Math.max(1, Math.round(diff / 60_000));
});

const progressPercent = computed(() => {
  const current = task.value;
  if (!current || !current.total) return 0;
  return Math.min(100, Math.round((current.processed / current.total) * 100));
});

const taskFailed = computed(
  () => task.value?.status === "FAILED" || task.value?.status === "PARTIAL_FAILED",
);

const importButtonText = computed(() => {
  const current = task.value;
  if (!importing.value) return "一键导入 Obsidian";
  if (current && current.status === "RUNNING") {
    return `正在导入 ${current.processed} / ${current.total}`;
  }
  return "正在等待 Obsidian…";
});

const taskText = computed(() => {
  const current = task.value;
  if (!current) return "";
  switch (current.status) {
    case "PENDING":
      return `已创建任务（${current.total} 篇），正在等待 Obsidian 响应…`;
    case "RUNNING":
      return `正在导入 ${current.processed} / ${current.total}`;
    case "COMPLETED":
      return `导入完成：成功 ${current.successCount}，跳过 ${current.skippedCount}`;
    case "PARTIAL_FAILED":
      return `导入完成，但有 ${current.failedCount + current.conflictCount} 个文件没成功（成功 ${current.successCount}，跳过 ${current.skippedCount}）`;
    case "FAILED":
      return `导入失败：${current.failedCount} 个文件全部失败`;
    case "EXPIRED":
      return "任务已过期，请重新点击导入";
    case "SUPERSEDED":
      return "这个任务已被新的导入请求替代";
    default:
      return current.status;
  }
});

const pairRemainText = computed(() => {
  if (pairSecondsLeft.value <= 0) return "配对码已过期，请点「换一个码」";
  const minutes = Math.floor(pairSecondsLeft.value / 60);
  const seconds = String(pairSecondsLeft.value % 60).padStart(2, "0");
  return `${minutes}:${seconds} 内有效`;
});

async function loadPluginStatus() {
  try {
    plugin.value = await fetchPluginStatus();
  } catch {
    plugin.value = { paired: false };
  }
}

function stopPairTimers() {
  if (pairPoll !== undefined) {
    clearInterval(pairPoll);
    pairPoll = undefined;
  }
  if (pairTick !== undefined) {
    clearInterval(pairTick);
    pairTick = undefined;
  }
}

async function refreshPairCode() {
  pairBusy.value = true;
  try {
    const issued = await createPairCode();
    pairCode.value = issued.code;
    pairSecondsLeft.value = issued.expiresInSeconds;
  } catch (error) {
    ui.toast(errorMessage(error, "生成配对码失败"), "error");
  } finally {
    pairBusy.value = false;
  }
}

/**
 * 连接 Obsidian 的唯一入口。
 *
 * 先让后端试着自己装（后端与 Obsidian 同机时可行，插件文件与 token 一并写好）；
 * 装不上就降级成「下载包 + 一条 obsidian:// 链接」——两条路的终点是同一份插件配置，
 * 所以用户看到的后续行为完全一致。
 */
async function connectObsidian() {
  if (installing.value) return;
  installing.value = true;
  connectOpen.value = true;
  connectStep.value = "manual";
  try {
    const result = await installObsidianPlugin();
    if (result.installed) {
      connectVaultName.value = result.vaultName || "";
      connectStep.value = "installed";
      // 先把界面切到「已连接」：token 已经落库，但 Obsidian 还没重载，所以显示「离线」
      plugin.value = {
        paired: true,
        installationId: result.installationId,
        vaultName: result.vaultName,
      };
      return;
    }
    connectStep.value = "manual";
  } catch {
    // 云端部署最常见的「失败」就是服务器上没有 obsidian.json，直接走手动路径
    connectStep.value = "manual";
  } finally {
    installing.value = false;
  }
}

/** 云端路径：用户把插件放好之后，生成一次性配对码并给出连接链接 */
async function startManualConnect() {
  await refreshPairCode();
  connectStep.value = "link";
  startPairTimers();
}

function startPairTimers() {
  stopPairTimers();
  pairTick = window.setInterval(() => {
    if (pairSecondsLeft.value > 0) pairSecondsLeft.value -= 1;
  }, 1000);
  // 插件一配对成功就自动关窗，用户不需要自己判断「好了没」
  pairPoll = window.setInterval(async () => {
    const status = await fetchPluginStatus().catch(() => null);
    if (status?.paired) {
      plugin.value = status;
      stopPairTimers();
      connectOpen.value = false;
      ui.toast(`已连接 Obsidian：${status.vaultName}`);
    }
  }, 2000);
}

function openConnectedVault() {
  const vault = connectVaultName.value;
  window.location.href = vault
    ? `obsidian://open?vault=${encodeURIComponent(vault)}`
    : "obsidian://open";
}

function closeStream() {
  streamClose?.();
  streamClose = null;
  if (offlineTimer !== undefined) {
    clearTimeout(offlineTimer);
    offlineTimer = undefined;
  }
}

function toastForTask(current: ImportTask) {
  if (current.status === "COMPLETED") {
    ui.toast(
      current.skippedCount > 0
        ? `导入完成：写入 ${current.successCount} 篇，跳过 ${current.skippedCount} 篇未变化`
        : `导入完成：写入 ${current.successCount} 篇`,
    );
  } else if (current.status === "PARTIAL_FAILED") {
    ui.toast(`导入完成，${current.failedCount + current.conflictCount} 个文件未成功`, "error");
  } else if (current.status === "FAILED") {
    ui.toast("导入失败，请查看失败明细", "error");
  } else if (current.status === "EXPIRED") {
    ui.toast("任务已过期，请重新导入", "error");
  }
  if (current.successCount > 0) {
    library.refreshDiaries();
    library.refreshExperiences();
    library.refreshKnowledge();
  }
}

/**
 * 用官方 obsidian://open 把 Obsidian 唤到前台：已开着就切窗口，没开则启动。
 * 浏览器会弹一次「是否允许此网站打开 Obsidian」，用户点取消也不影响——
 * 插件平时挂着长轮询，任务建好后会被立即唤醒并领走，唤醒只是让窗口先到前台、更有反馈。
 */
function wakeObsidian() {
  const vault = plugin.value.vaultName;
  if (!vault) return;
  window.location.href = `obsidian://open?vault=${encodeURIComponent(vault)}`;
}

async function startPluginImport() {
  importing.value = true;
  closeStream();
  try {
    const created = await createImportTask();
    task.value = created;
    wakeObsidian();
    streamClose = subscribeImportTask(created.taskId, (next) => {
      task.value = next;
      if (!isFinished(next.status)) {
        // 30 秒超时后按钮已复原；插件这时才上线的话，把界面拉回「正在导入」
        importing.value = true;
      }
      if (next.status === "RUNNING" && offlineTimer !== undefined) {
        clearTimeout(offlineTimer);
        offlineTimer = undefined;
      }
      if (isFinished(next.status)) {
        closeStream();
        importing.value = false;
        toastForTask(next);
      }
    });
    // 30 秒没有插件接手，多半是 Obsidian 没开。不取消任务：插件上线后会继续领
    offlineTimer = window.setTimeout(() => {
      if (task.value?.status === "PENDING") {
        importing.value = false;
        ui.toast("没等到 Obsidian 响应，请确认它已打开并启用了插件；任务会在插件上线后自动继续", "error");
      }
    }, 30_000);
  } catch (error) {
    importing.value = false;
    ui.toast(errorMessage(error, "创建导入任务失败"), "error");
  }
}

onBeforeUnmount(() => {
  closeStream();
  stopPairTimers();
  if (statusTimer !== undefined) {
    clearInterval(statusTimer);
    statusTimer = undefined;
  }
});

onMounted(async () => {
  await ui.loadProfile();
  Object.assign(form, ui.profile);
  loadObsidian();
  loadPluginStatus();
  // 重载 Obsidian 后插件才开始心跳，这里轻量探一次就能把「离线」翻成「在线」
  statusTimer = window.setInterval(() => {
    if (plugin.value.paired && !pluginOnline.value) void loadPluginStatus();
  }, 10_000);
});

async function loadObsidian() {
  try {
    obsidian.value = await fetchObsidianPreview();
    obsidianError.value = "";
  } catch (error) {
    obsidianError.value = errorMessage(error, "无法读取导出统计，请确认后端已重启");
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
