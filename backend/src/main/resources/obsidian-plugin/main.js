"use strict";

/**
 * MemoAgent 的 Obsidian 插件（免构建：手写 CommonJS，不需要 npm / 打包）。
 *
 * 它是「同步到 Obsidian」唯一执行者——本地部署与云端部署跑的是同一份代码，
 * 用户看到的行为完全一致。两种部署只差「插件怎么装到这台机器、配置怎么填」：
 *   - 后端与 Obsidian 同机：后端直接把本文件写进 .obsidian/plugins/memoagent/，
 *     连 data.json（含 token）都一并写好，用户重载 Obsidian 即可。
 *   - 后端在云端：用户下载插件包解压，点网页上的「连接」按钮，
 *     浏览器打开 obsidian://memoagent-pair?host=...&code=...，由下面
 *     registerObsidianProtocolHandler 收到参数自动配对，不需要手抄 6 位码。
 */

const {
  Notice,
  Plugin,
  PluginSettingTab,
  Setting,
  TFile,
  TFolder,
  normalizePath,
  requestUrl,
} = require("obsidian");
const { createHash, randomUUID } = require("crypto");

/** 所有笔记都写在这个子目录下，绝不碰 Vault 其它位置 */
const ROOT = "MemoAgent";
/** 每处理多少篇上报一次进度 */
const PROGRESS_EVERY = 10;
/** 长轮询挂起时长：有任务立刻返回，没有就等这么久再重发。20 秒给代理的读超时留足余量 */
const LONG_POLL_SECONDS = 20;
/** 出网错误后的退避：网络抖动时不要立刻重试，否则一秒能刷出几十条通知 */
const RETRY_DELAY_MS = 8_000;

const DEFAULT_SETTINGS = {
  serverUrl: "http://localhost:8080",
  token: "",
  installationId: "",
  vaultName: "",
  /** 只在设置面板里临时用，配对成功后清空 */
  pairCodeInput: "",
};

/* ------------------------------------------------------------------ */
/* 内容指纹                                                            */
/* ------------------------------------------------------------------ */

/**
 * 必须与后端 Digests.contentHash 完全一致：
 * 1. 先把 CRLF / CR 归一成 LF（Windows 上别处编辑过会变成 CRLF，不归一化会一直误判为「本地被改」）；
 * 2. UTF-8 取字节做 SHA-256；
 * 3. 取前 8 字节转 16 位小写十六进制。
 */
function normalizeNewlines(text) {
  return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}

function contentHash(text) {
  return createHash("sha256").update(normalizeNewlines(text), "utf8").digest("hex").slice(0, 16);
}

/* ------------------------------------------------------------------ */
/* HTTP 客户端                                                         */
/* ------------------------------------------------------------------ */

/** 带 HTTP 状态码的错误，401 要单独处理（清掉本地 token 让用户重新配对） */
class ApiError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

/**
 * 用 requestUrl 而不是 fetch：它走 Electron 的 Node 网络栈，不受 CORS 限制。
 * 后端若是云端域名、插件在本机跑，fetch 会被同源策略拦下，requestUrl 不会。
 */
class MemoAgentApi {
  constructor(serverUrl, token) {
    this.base = String(serverUrl || "").trim().replace(/\/+$/, "");
    this.token = token;
  }

  pair(code, installationId, vaultName) {
    return this.call("/api/obsidian/plugin/pair", "POST", { code, installationId, vaultName }, false);
  }

  me() {
    return this.call("/api/obsidian/plugin/me", "GET");
  }

  /** data 为 null 表示当前没有待处理任务 */
  pendingTask() {
    return this.call("/api/obsidian/plugin/tasks/pending", "GET");
  }

  /**
   * 长轮询：没任务时服务端把请求挂住，任务一建好立刻返回。
   * 返回 null 表示「等满了还没任务」，调用方应当立即重发。
   */
  waitTask(timeoutSeconds) {
    return this.call(`/api/obsidian/plugin/tasks/wait?timeout=${timeoutSeconds}`, "GET");
  }

  progress(taskId, progress) {
    return this.call(`/api/obsidian/plugin/tasks/${taskId}/progress`, "POST", progress);
  }

  complete(taskId, payload) {
    return this.call(`/api/obsidian/plugin/tasks/${taskId}/complete`, "POST", payload);
  }

  async call(path, method, body, withToken = true) {
    const headers = { "Content-Type": "application/json" };
    if (withToken && this.token) {
      headers["X-Plugin-Token"] = this.token;
    }

    const response = await requestUrl({
      url: `${this.base}${path}`,
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      // 非 2xx 也要拿到响应体里的 message，而不是抛一个只有状态码的异常
      throw: false,
    });

    if (response.status >= 400) {
      throw new ApiError(response.status, messageOf(response.text) || `HTTP ${response.status}`);
    }
    if (!response.text) {
      return undefined;
    }

    let parsed;
    try {
      parsed = JSON.parse(response.text);
    } catch {
      throw new ApiError(response.status, "服务器返回了无法解析的响应");
    }
    if (parsed.code !== 200) {
      throw new ApiError(parsed.code, parsed.message || "请求失败");
    }
    return parsed.data;
  }
}

function messageOf(text) {
  try {
    const parsed = JSON.parse(text);
    return parsed && parsed.message ? parsed.message : null;
  } catch {
    return null;
  }
}

/* ------------------------------------------------------------------ */
/* 任务执行                                                            */
/* ------------------------------------------------------------------ */

/**
 * 执行一个导入任务。
 *
 * 串行写入，不并发：Obsidian 每写一个文件都会触发元数据缓存更新与索引，
 * 200 个文件同时写会把界面卡住，而且失败后无法判断哪些已完成。
 *
 * 单篇失败不中断整批——100 个文件里有 1 个坏名字，不能让另外 99 个白跑。
 */
async function syncTask(app, task, onProgress) {
  const results = [];
  let success = 0;
  let skipped = 0;
  let failed = 0;
  let conflict = 0;

  for (let index = 0; index < task.files.length; index++) {
    const file = task.files[index];
    let result;
    try {
      result = await applyFile(app, file);
    } catch (error) {
      result = { path: file.path, status: "FAILED", error: describe(error) };
    }

    results.push(result);
    if (result.status === "SUCCESS") {
      success++;
    } else if (result.status === "SKIPPED") {
      skipped++;
    } else if (result.status === "CONFLICT") {
      conflict++;
    } else {
      failed++;
    }

    const done = index + 1;
    if (done % PROGRESS_EVERY === 0 || done === task.files.length) {
      // 上报失败不影响继续写盘：进度只是给页面看的，本地文件已经落下了
      await onProgress({ processed: done, success, skipped, failed, conflict, message: "" }).catch(
        () => undefined,
      );
    }
  }

  const total = task.files.length;
  let status = "COMPLETED";
  if (failed + conflict > 0) {
    status = failed + conflict >= total && total > 0 ? "FAILED" : "PARTIAL_FAILED";
  }

  return {
    status,
    successCount: success,
    skippedCount: skipped,
    failedCount: failed,
    conflictCount: conflict,
    message: failed + conflict > 0 ? `${failed} 个失败，${conflict} 个冲突` : "",
    files: results,
  };
}

async function applyFile(app, file) {
  // 先校验原始路径：normalizePath 会把反斜杠转正，等转完再检查就晚了
  if (!isSafePath(file.path)) {
    return { path: file.path, status: "FAILED", error: "路径不合法，已拒绝写入" };
  }
  const path = normalizePath(file.path);
  const baseline = file.lastSyncedHash || "";

  if (file.opType === "DELETE") {
    const target = app.vault.getAbstractFileByPath(path);
    if (!(target instanceof TFile)) {
      return { path, status: "SKIPPED" };
    }
    // 本地改过的文件不删：删掉就找不回来了，宁可留一个冲突让人自己确认
    if (baseline) {
      const local = await app.vault.read(target);
      if (contentHash(local) !== baseline) {
        return { path, status: "CONFLICT", error: "本地改过，未删除（确认后可手动删）" };
      }
    }
    if (typeof app.fileManager.trashFile === "function") {
      await app.fileManager.trashFile(target);
    } else {
      await app.vault.delete(target);
    }
    return { path, status: "SUCCESS" };
  }

  const remote = file.content == null ? "" : file.content;
  const existing = app.vault.getAbstractFileByPath(path);

  if (existing instanceof TFile) {
    const local = await app.vault.read(existing);
    if (local === remote) {
      return { path, status: "SKIPPED" };
    }

    const localHash = contentHash(local);

    // 首次同步（没有基线）：本地同名文件以服务端为准，否则用户点了导入却什么都没变
    if (!baseline) {
      await app.vault.modify(existing, remote);
      return { path, status: "SUCCESS" };
    }
    // 本地没动过、远端变了：安全更新
    if (localHash === baseline) {
      await app.vault.modify(existing, remote);
      return { path, status: "SUCCESS" };
    }
    // 远端没变、本地改过：保留用户改动，下次服务端真变了再覆盖
    if (file.hash === baseline) {
      return { path, status: "SKIPPED" };
    }
    // 双方都改过：不静默覆盖，留给用户判断
    return { path, status: "CONFLICT", error: "本地与 MemoAgent 都改过，已保留本地文件" };
  }

  await ensureFolder(app, path);
  await app.vault.create(path, remote);
  return { path, status: "SUCCESS" };
}

/** 递归建目录：不要求用户提前手动建好 Diary / Experience / Knowledge */
async function ensureFolder(app, filePath) {
  const segments = filePath.split("/");
  segments.pop();
  let current = "";
  for (const segment of segments) {
    current = current ? `${current}/${segment}` : segment;
    const existing = app.vault.getAbstractFileByPath(current);
    if (!(existing instanceof TFolder)) {
      // 已存在时 createFolder 会抛错，并发/残留目录都可能走到这里，忽略即可
      await app.vault.createFolder(current).catch(() => undefined);
    }
  }
}

function isSafePath(path) {
  if (!path || !path.startsWith(`${ROOT}/`) || !path.endsWith(".md")) {
    return false;
  }
  return !path.includes("..") && !path.includes("\\") && !path.includes("//");
}

/* ------------------------------------------------------------------ */
/* 插件主体                                                            */
/* ------------------------------------------------------------------ */

class MemoAgentPlugin extends Plugin {
  settings = Object.assign({}, DEFAULT_SETTINGS);
  /** 长轮询循环是否还在跑。卸载插件时置 false，挂起中的请求会在超时后自然退出 */
  running = false;
  busy = false;
  /** 上一次报过的错误，用来抑制「反复弹同一个红字」 */
  lastError = "";

  async onload() {
    await this.loadSettings();
    this.addSettingTab(new MemoAgentSettingTab(this.app, this));

    // 网页上的「连接 Obsidian」按钮会打开 obsidian://memoagent-pair?host=...&code=...
    // 这里接住参数直接完成配对，用户不用手抄 6 位码。
    this.registerObsidianProtocolHandler("memoagent-pair", (params) => {
      void this.pairFromLink(params);
    });

    // 等 Vault 索引就绪再读文件，否则启动瞬间 getAbstractFileByPath 全是 null
    this.app.workspace.onLayoutReady(() => {
      // 循环的第一个请求本身就是「立刻查一次」，不必先单独跑一遍
      this.startPolling();
    });

    this.addCommand({
      id: "import-now",
      name: "立即从 MemoAgent 导入",
      callback: () => void this.pollNow(),
    });
  }

  onunload() {
    this.stopPolling();
  }

  async loadSettings() {
    const data = await this.loadData();
    this.settings = Object.assign({}, DEFAULT_SETTINGS, data || {});
    if (!this.settings.installationId) {
      // 安装标识：服务端据此区分「这台机器」，卸载重装会换一个
      this.settings.installationId = randomUUID();
      await this.saveSettings();
    }
  }

  async saveSettings() {
    await this.saveData(this.settings);
  }

  /** 配对：用网页上的 6 位码换长期 token */
  async pair(code) {
    if (!code) {
      new Notice("请先填写 6 位配对码");
      return;
    }
    if (!this.settings.serverUrl) {
      new Notice("请先填写 MemoAgent 服务器地址");
      return;
    }
    try {
      const api = new MemoAgentApi(this.settings.serverUrl, "");
      const result = await api.pair(code, this.settings.installationId, this.app.vault.getName());
      this.settings.token = result.pluginToken;
      this.settings.vaultName = result.vaultName;
      this.settings.pairCodeInput = "";
      await this.saveSettings();
      this.lastError = "";
      new Notice(`已连接 MemoAgent：${result.vaultName}`);
      this.startPolling();
    } catch (error) {
      new Notice(`连接失败：${describe(error)}`, 6000);
    }
  }

  /** 从 obsidian:// 链接配对。params 已由 Obsidian 解好码，直接取字段即可。 */
  async pairFromLink(params) {
    const code = (params && params.code) || "";
    const host = (params && params.host) || "";
    if (host) {
      this.settings.serverUrl = host;
      await this.saveSettings();
    }
    if (!code) {
      new Notice("连接链接里缺少配对码，请在 MemoAgent 页面重新点「连接」");
      return;
    }
    await this.pair(code);
  }

  async disconnect() {
    // 先停循环再清 token：否则挂起的那次长轮询会带着空 token 再发一轮
    this.stopPolling();
    this.settings.token = "";
    this.settings.vaultName = "";
    await this.saveSettings();
  }

  startPolling() {
    if (this.running) {
      return;
    }
    this.running = true;
    void this.loop();
  }

  stopPolling() {
    // 只翻标志位：正在挂起的那次长轮询会在超时后自然返回，不需要强拆连接
    this.running = false;
  }

  /**
   * 长轮询循环。
   *
   * 取代了「每 8 秒问一次」：空闲时 20 秒才一个请求，但网页一建任务，
   * 服务端立刻把挂着的请求放行，这边重发一次就拿到任务，延迟只剩一次本地往返。
   *
   * 同步期间循环停在 await 里，不会重复领同一个任务，所以不需要像以前那样
   * 显式 stopPolling / startPolling。中途关掉 Obsidian 也没关系——服务端任务还在，
   * 下次启动继续领，已写好的文件靠 hash 判等跳过，天然幂等。
   */
  async loop() {
    while (this.running) {
      if (!this.settings.token) {
        this.running = false;
        return;
      }

      let task;
      try {
        const api = new MemoAgentApi(this.settings.serverUrl, this.settings.token);
        task = await api.waitTask(LONG_POLL_SECONDS);
        this.lastError = "";
      } catch (error) {
        await this.reportError(error);
        if (!this.running) {
          return;
        }
        // 网络抖动、后端重启：退避一会儿再来
        await sleep(RETRY_DELAY_MS);
        continue;
      }

      if (!this.running) {
        return;
      }
      if (!task) {
        // 等满了，或者刚被网页唤醒——两种都意味着「立刻再问一次」
        continue;
      }

      const ok = await this.execute(task);
      if (!ok) {
        await sleep(RETRY_DELAY_MS);
      }
    }
  }

  /** 执行一个任务。返回 false 表示中途失败，调用方应退避后重试（服务端任务仍在，重领是幂等的）。 */
  async execute(task) {
    if (this.busy) {
      return true;
    }
    this.busy = true;
    try {
      const api = new MemoAgentApi(this.settings.serverUrl, this.settings.token);
      new Notice(`MemoAgent：开始导入 ${task.total} 篇`);

      const outcome = await syncTask(this.app, task, (progress) =>
        api.progress(task.taskId, progress),
      );
      await api.complete(task.taskId, outcome);
      new Notice(describeOutcome(outcome), 6000);
      this.lastError = "";
      return true;
    } catch (error) {
      new Notice(`MemoAgent 导入失败：${describe(error)}`, 6000);
      return false;
    } finally {
      this.busy = false;
    }
  }

  /** 设置面板的「现在检查」：不走长轮询，问一次就给出明确答复 */
  async pollNow() {
    if (!this.settings.token) {
      new Notice("还没连接 MemoAgent，请先在设置里完成连接");
      return;
    }
    if (this.busy) {
      return;
    }
    try {
      const api = new MemoAgentApi(this.settings.serverUrl, this.settings.token);
      const task = await api.pendingTask();
      if (!task) {
        new Notice("没有待处理的导入任务");
        return;
      }
      await this.execute(task);
    } catch (error) {
      await this.reportError(error);
    }
  }

  /**
   * 统一处理出网错误。
   *
   * 401 时先看磁盘上是不是已经换了新 token —— 网页点「重新连接」时后端会直接改写 data.json，
   * 而本插件内存里还是旧的。这时候要是直接 saveSettings()，会把刚写好的 token 抹成空串。
   */
  async reportError(error) {
    const message = describe(error);
    // 网络抖动时不要反复弹同一个红字
    if (message !== this.lastError) {
      this.lastError = message;
      new Notice(`MemoAgent 同步失败：${message}`, 6000);
    }

    if (!(error instanceof ApiError) || error.code !== 401) {
      return;
    }
    const fresh = await this.loadData();
    if (fresh && fresh.token && fresh.token !== this.settings.token) {
      this.settings.token = fresh.token;
      this.settings.serverUrl = fresh.serverUrl || this.settings.serverUrl;
    } else {
      this.settings.token = "";
      // 凭证没救了：停掉长轮询，别一直拿 401 撞墙
      this.running = false;
    }
    await this.saveSettings();
  }
}

/* ------------------------------------------------------------------ */
/* 设置面板                                                            */
/* ------------------------------------------------------------------ */

class MemoAgentSettingTab extends PluginSettingTab {
  constructor(app, plugin) {
    super(app, plugin);
    this.plugin = plugin;
  }

  display() {
    const { containerEl } = this;
    containerEl.empty();

    containerEl.createEl("h2", { text: "MemoAgent" });
    containerEl.createEl("p", {
      text: "单向导入：把 MemoAgent 里的日记、经验、知识写进当前 Vault 的 MemoAgent/ 目录。在 Obsidian 里改的内容不会回流。",
      cls: "setting-item-description",
    });

    new Setting(containerEl)
      .setName("服务器地址")
      .setDesc("本机部署默认 http://localhost:8080；部署在云端时填完整域名。用网页上的「连接」按钮接入时会自动填好。")
      .addText((text) =>
        text
          .setPlaceholder("http://localhost:8080")
          .setValue(this.plugin.settings.serverUrl)
          .onChange(async (value) => {
            this.plugin.settings.serverUrl = value.trim();
            await this.plugin.saveSettings();
          }),
      );

    const connected = !!this.plugin.settings.token;

    new Setting(containerEl)
      .setName("连接状态")
      .setDesc(
        connected
          ? `已连接：${this.plugin.settings.vaultName || this.app.vault.getName()}`
          : "在 MemoAgent 设置页点「连接 Obsidian」，按提示接入。连不上时可以在这里手动填 6 位配对码。",
      )
      .addText((text) => {
        if (connected) {
          text.setValue("——").setDisabled(true);
          return;
        }
        text.setPlaceholder("6 位数字").onChange((value) => {
          this.plugin.settings.pairCodeInput = value.trim();
        });
      })
      .addButton((button) =>
        button
          .setButtonText(connected ? "重新配对" : "连接")
          .setCta()
          .onClick(async () => {
            await this.plugin.pair(this.plugin.settings.pairCodeInput);
            this.display();
          }),
      );

    new Setting(containerEl)
      .setName("立即同步")
      .setDesc("平时插件挂一个长连接等任务，网页上一点就立即开始。这个按钮是不等连接、现在问一次。")
      .addButton((button) =>
        button.setButtonText("现在检查").onClick(() => void this.plugin.pollNow()),
      );

    if (connected) {
      new Setting(containerEl)
        .setName("断开连接")
        .setDesc("清掉本地保存的凭证。之后要重新导入，需要在网页上重新点「连接」。")
        .addButton((button) =>
          button
            .setButtonText("断开")
            .setWarning()
            .onClick(async () => {
              await this.plugin.disconnect();
              new Notice("已断开 MemoAgent");
              this.display();
            }),
        );
    }
  }
}

/* ------------------------------------------------------------------ */
/* 工具                                                                */
/* ------------------------------------------------------------------ */

function describeOutcome(outcome) {
  const parts = [`成功 ${outcome.successCount}`, `跳过 ${outcome.skippedCount}`];
  if (outcome.failedCount > 0) {
    parts.push(`失败 ${outcome.failedCount}`);
  }
  if (outcome.conflictCount > 0) {
    parts.push(`冲突 ${outcome.conflictCount}`);
  }
  return `导入完成：${parts.join("，")}`;
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function describe(error) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return String(error);
}

module.exports = MemoAgentPlugin;
