<template>
  <div class="fade-in settings-page">
    <div class="page-head">
      <div class="grow">
        <h1 class="h1">设置</h1>
        <p class="lede" style="margin-bottom: 0">管理你的回顾、AI 行为与知识导出</p>
      </div>
    </div>

    <div class="sections">
      <!-- 回顾与沉淀 -->
      <section class="pane">
        <h2 class="pane-title">回顾与沉淀</h2>

        <div class="item">
          <div class="item-main">
            <div class="item-label">每周回顾</div>
            <div class="item-hint">每周自动生成一次回顾草稿，到点后出现在顶栏待办</div>
          </div>
          <ToggleSwitch v-model="form.weeklyReview" />
        </div>

        <div v-if="form.weeklyReview" class="sub">
          <div class="sub-label">生成时间</div>
          <div class="pickers">
            <select v-model.number="form.reviewDay" class="picker">
              <option :value="0">周日</option>
              <option :value="1">周一</option>
              <option :value="2">周二</option>
              <option :value="3">周三</option>
              <option :value="4">周四</option>
              <option :value="5">周五</option>
              <option :value="6">周六</option>
            </select>
            <input v-model="form.reviewTime" class="picker" type="time" />
          </div>
          <p class="item-hint" style="margin: 0">
            到点后不会直接生成：待办里出现「本周回顾待生成」，由你确认后一键生成。
          </p>
        </div>

        <div class="hr" />

        <div class="item">
          <div class="item-main">
            <div class="item-label">采访结束后自动结构化提炼</div>
            <div class="item-hint">问题 / 原因 / 方案 / 经验，直接产出经验草稿</div>
          </div>
          <ToggleSwitch v-model="form.autoExtract" />
        </div>

        <p class="save-state" :class="saveState">
          <template v-if="saveState === 'saving'">保存中…</template>
          <template v-else-if="saveState === 'saved'">已保存</template>
          <template v-else-if="saveState === 'error'">保存失败，改动只留在本地</template>
        </p>
      </section>

      <!-- 数据 -->
      <section class="pane">
        <h2 class="pane-title">数据</h2>
        <div class="stats">
          <div class="stat-cell">
            <b>{{ stats.diaries }}</b>
            <span>日记</span>
          </div>
          <div class="stat-cell">
            <b>{{ stats.experiences }}</b>
            <span>经验</span>
          </div>
          <div class="stat-cell">
            <b>{{ stats.knowledge }}</b>
            <span>知识</span>
          </div>
        </div>
        <p class="pane-foot">最近更新 {{ lastUpdatedText }}</p>
      </section>

      <!-- Obsidian：整个页面唯一的主操作 -->
      <section class="pane">
        <h2 class="pane-title">Obsidian</h2>

        <div class="status">
          <span class="dot" :class="obsidianStatus.tone" />
          <div class="status-main">
            <div class="item-label">{{ obsidianStatus.label }}</div>
            <div class="item-hint">{{ obsidianStatus.hint }}</div>
          </div>
        </div>

        <template v-if="plugin.paired">
          <button
            class="btn primary lg block action"
            :disabled="importing || obsidianBusy"
            @click="startPluginImport"
          >
            {{ importButtonText }}
          </button>

          <div v-if="task" class="stack" style="gap: 6px">
            <div
              style="height: 4px; border-radius: 999px; background: var(--line-2); overflow: hidden"
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
            <p class="item-hint" style="margin: 0">{{ taskText }}</p>
            <p
              v-if="task.problems?.length"
              class="item-hint"
              style="margin: 0; color: var(--danger); word-break: break-all"
            >
              首个问题：{{ task.problems[0]?.path
              }}<template v-if="task.problems[0]?.error">（{{ task.problems[0]?.error }}）</template>
            </p>
          </div>

          <p
            v-if="plugin.lastActiveAt && !pluginOnline && !importing"
            class="item-hint"
            style="margin: 0"
          >
            打开 Obsidian 后，未完成的任务会自动继续。
          </p>

          <p class="pane-foot">
            <template v-if="lastSync">最近同步 {{ formatSyncTime(lastSync.at) }} · 写入 {{ lastSync.success }} 篇</template>
            <template v-else>还没有从这个设备同步过</template>
            <span class="sep">·</span>
            <button class="link-btn" @click="connectObsidian">重新连接</button>
          </p>
        </template>

        <template v-else>
          <button class="btn primary lg block action" :disabled="installing" @click="connectObsidian">
            {{ installing ? "正在准备…" : "连接 Obsidian" }}
          </button>
          <p class="pane-foot">
            连一次，之后点一下就写进你电脑上 Obsidian 的 MemoAgent/ 目录。不用选目录，也不用记服务器地址。
          </p>
        </template>

        <details class="more">
          <summary>其他导入方式</summary>
          <div class="stack" style="gap: 8px; margin-top: 10px">
            <p class="item-hint" style="margin: 0">
              没装 Obsidian、或想自己决定放在哪里：下载 Markdown 包，解压到任意目录。
            </p>
            <button class="btn sm" :disabled="obsidianBusy" @click="exportObsidianZip">
              下载 Markdown 包（{{ obsidian.total }} 篇）
            </button>
            <p v-if="obsidianError" class="item-hint" style="margin: 0; color: var(--danger)">
              {{ obsidianError }}
            </p>
          </div>
        </details>
      </section>

      <!-- 高级：尽量不出现在视线中心 -->
      <section class="pane pane-quiet">
        <h2 class="pane-title">高级</h2>

        <div class="item">
          <div class="item-main">
            <div class="item-label">重新加载资产</div>
            <div class="item-hint">从后端再拉一遍日记 / 经验 / 知识</div>
          </div>
          <button class="link-btn" @click="resetSeed">重新加载</button>
        </div>

        <div class="hr" />

        <div class="item">
          <div class="item-main">
            <div class="item-label">清空本地设置</div>
            <div class="item-hint">
              只清浏览器里的会话与偏好，不会删除后端数据，也不会动 Obsidian 里的 Markdown 文件
            </div>
          </div>
          <button class="link-btn danger" @click="clearAll">清空</button>
        </div>
      </section>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import ModalDialog from "@/components/base/ModalDialog.vue";
import ToggleSwitch from "@/components/base/ToggleSwitch.vue";
import { saveProfileApi } from "@/api/profile";
import { errorMessage } from "@/api/http";
import { downloadObsidianZip, fetchObsidianPreview, type ObsidianPreview } from "@/api/obsidian";
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

const obsidian = ref<ObsidianPreview>({ diaries: 0, experiences: 0, knowledge: 0, total: 0 });
const obsidianBusy = ref(false);
const obsidianError = ref("");

/* ---- 即时保存：改完就写后端，不再有「保存偏好」按钮 ---- */

const saveState = ref<"idle" | "saving" | "saved" | "error">("idle");
/** 首屏回填期间不能触发保存，否则会把后端读数原样写回一次 */
let saveArmed = false;
let saveTimer: number | undefined;
let saveFlashTimer: number | undefined;

function queueSave() {
  if (!saveArmed) return;
  saveState.value = "saving";
  if (saveTimer !== undefined) clearTimeout(saveTimer);
  saveTimer = window.setTimeout(() => {
    saveTimer = undefined;
    void persistProfile();
  }, 500);
}

async function persistProfile() {
  const payload: ProfileSettings = {
    displayName: form.displayName.trim() || "未命名",
    signature: form.signature,
    reviewDay: form.reviewDay,
    reviewTime: form.reviewTime,
    autoExtract: form.autoExtract,
    weeklyReview: form.weeklyReview,
  };
  // 先落本地：网络慢的时候界面不需要等
  ui.saveProfile(payload);
  try {
    await saveProfileApi(payload);
    saveState.value = "saved";
    if (saveFlashTimer !== undefined) clearTimeout(saveFlashTimer);
    saveFlashTimer = window.setTimeout(() => {
      if (saveState.value === "saved") saveState.value = "idle";
    }, 1600);
  } catch (error) {
    saveState.value = "error";
    ui.toast(errorMessage(error, "设置保存失败"), "error");
  }
}

watch(() => [form.weeklyReview, form.autoExtract, form.reviewDay, form.reviewTime], queueSave);

/** 数据区「最近更新」：三类资产里最新的一个时间 */
const lastUpdatedText = computed(() => {
  const stamps: number[] = [];
  const push = (value?: string) => {
    if (!value) return;
    const time = new Date(value).getTime();
    if (!Number.isNaN(time)) stamps.push(time);
  };
  library.diaries.forEach((row) => {
    push(row.createdAt);
    push(row.date);
  });
  library.experiences.forEach((row) => push(row.createdAt));
  library.knowledge.forEach((row) => push(row.updatedAt));
  if (!stamps.length) return "暂无记录";
  const latest = new Date(Math.max(...stamps));
  const today = new Date();
  if (latest.toDateString() === today.toDateString()) return formatClock(latest);
  return `${latest.getMonth() + 1} 月 ${latest.getDate()} 日`;
});

function formatClock(date: Date) {
  return `今天 ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

/* ---- 最近一次同步：存在本机，换设备就重新算 ---- */

const LAST_SYNC_KEY = "memoagent:obsidian-last-sync";

interface LastSync {
  at: string;
  success: number;
}

const lastSync = ref<LastSync | null>(readLastSync());

function readLastSync(): LastSync | null {
  try {
    const raw = localStorage.getItem(LAST_SYNC_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LastSync;
    return parsed?.at ? parsed : null;
  } catch {
    return null;
  }
}

function rememberSync(current: ImportTask) {
  const record: LastSync = { at: new Date().toISOString(), success: current.successCount };
  lastSync.value = record;
  try {
    localStorage.setItem(LAST_SYNC_KEY, JSON.stringify(record));
  } catch {
    // 隐私模式下写不进去，只影响这一行提示
  }
}

function formatSyncTime(iso: string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "未知时间";
  const clock = `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  if (date.toDateString() === new Date().toDateString()) return `今天 ${clock}`;
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${clock}`;
}

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

const idleText = computed(() => {
  const stamp = plugin.value.lastActiveAt;
  if (!stamp) return "未知";
  const diff = Date.now() - new Date(stamp).getTime();
  const minutes = Math.round(diff / 60_000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  return `${Math.round(hours / 24)} 天前`;
});

/** 状态只留一份语义：标题说「连没连上」，说明说「绑的是哪个库」 */
const obsidianStatus = computed(() => {
  const current = plugin.value;
  if (!current.paired) {
    return {
      tone: "off",
      label: "未连接",
      hint: "连一次之后，这个按钮就会直接写进你的 Vault。",
    };
  }
  if (!current.lastActiveAt) {
    return {
      tone: "wait",
      label: "已连接",
      hint: `${current.vaultName || "Vault"} · 等 Obsidian 重载后生效`,
    };
  }
  if (pluginOnline.value) {
    return { tone: "on", label: "已连接", hint: `${current.vaultName} · 在线` };
  }
  return {
    tone: "idle",
    label: "未响应",
    hint: `${current.vaultName} · 上次响应 ${idleText.value}`,
  };
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
    rememberSync(current);
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
  if (saveFlashTimer !== undefined) clearTimeout(saveFlashTimer);
  // 关页面前把待写的那一次落掉，避免「改完立刻走」丢改动
  if (saveTimer !== undefined) {
    clearTimeout(saveTimer);
    saveTimer = undefined;
    void persistProfile();
  }
});

onMounted(async () => {
  await ui.loadProfile();
  Object.assign(form, ui.profile);
  // 回填完成之后再允许自动保存
  window.setTimeout(() => {
    saveArmed = true;
  }, 0);
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

async function resetSeed() {
  await library.resetToSeed();
  ui.toast("已从后端重新加载日记 / 经验 / 知识");
}

function clearAll() {
  const ok = window.confirm(
    "确定要清除本地连接信息吗？\n\n这只会清空浏览器里的会话与偏好。不会删除 MemoAgent 后端的数据，也不会删除 Obsidian 中的 Markdown 文件。",
  );
  if (!ok) return;
  Object.keys(localStorage)
    .filter((key) => key.startsWith("memoagent:"))
    .forEach((key) => localStorage.removeItem(key));
  ui.toast("本地设置已清空，正在重新载入…");
  window.setTimeout(() => window.location.reload(), 600);
}
</script>

<style scoped>
/* 单列、窄栏并整体居中：设置页读起来像文档，不像后台 */
.settings-page {
  max-width: 720px;
  margin: 0 auto;
  padding-top: 8px;
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pane {
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 55%, transparent);
}

.pane-quiet {
  background: transparent;
  border-color: var(--line);
}

.pane-title {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--ink);
}

/* 设置项：左文右控件 */
.item {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  justify-content: space-between;
}

.item-main {
  min-width: 0;
}

.item-label {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--ink);
}

.item-hint {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--faint);
}

.hr {
  height: 1px;
  margin: 14px 0;
  background: var(--line);
}

/* 时间选择：去掉原生控件的笨重边框 */
.sub {
  margin: 12px 0 0;
  padding-left: 12px;
  border-left: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sub-label {
  font-size: 12px;
  color: var(--mute);
}

.pickers {
  display: flex;
  gap: 8px;
}

.picker {
  padding: 7px 10px;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--ink);
  font-size: 13px;
  outline: none;
  transition:
    border-color 0.14s ease,
    background 0.14s ease;
}

.picker:hover {
  border-color: var(--line-1);
}

.picker:focus {
  border-color: var(--accent-line);
  background: var(--surface);
}

select.picker {
  min-width: 96px;
}

input.picker {
  min-width: 106px;
  font-family: var(--mono);
}

/* 即时保存的反馈：不占位、不跳动 */
.save-state {
  margin: 12px 0 0;
  height: 14px;
  font-size: 11.5px;
  color: var(--faint);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.save-state.saving,
.save-state.saved,
.save-state.error {
  opacity: 1;
}

.save-state.error {
  color: var(--danger);
}

/* 数据：一行三个数，不做三个大黑盒 */
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2px;
}

.stat-cell {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 2px 0;
}

.stat-cell b {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.stat-cell span {
  font-size: 12px;
  color: var(--mute);
}

.pane-foot {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--faint);
}

.sep {
  margin: 0 6px;
  color: var(--line-1);
}

/* 状态点：一条语义，不再重复两遍 */
.status {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.dot {
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--faint);
  flex: none;
}

.dot.on {
  background: var(--ok);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ok) 16%, transparent);
}

.dot.wait,
.dot.idle {
  background: var(--warn);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--warn) 14%, transparent);
}

/* 主操作：整页唯一的重按钮 */
.action {
  margin-bottom: 4px;
}

.more {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.more > summary {
  font-size: 12px;
  color: var(--faint);
  cursor: pointer;
  list-style: none;
}

.more > summary::-webkit-details-marker {
  display: none;
}

.more > summary::before {
  content: "▸";
  display: inline-block;
  margin-right: 6px;
  transition: transform 0.16s ease;
}

.more[open] > summary::before {
  transform: rotate(90deg);
}

.more > summary:hover {
  color: var(--mute);
}

/* 文字型操作：危险动作不该长得像普通按钮 */
.link-btn {
  flex: none;
  padding: 0;
  border: 0;
  background: none;
  color: var(--mute);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.14s ease;
}

.link-btn:hover {
  color: var(--ink);
}

.link-btn.danger {
  color: color-mix(in srgb, var(--danger) 78%, transparent);
}

.link-btn.danger:hover {
  color: var(--danger);
}
</style>
