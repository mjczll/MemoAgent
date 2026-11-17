import { request } from "./http";

/**
 * 插件式导入的 Web 侧接口。
 *
 * 与 obsidian.ts（FSA / zip / 后端直写）分开：那条路是"浏览器或后端直接写盘"，
 * 这条路是"插件在用户电脑上写盘"，两者的失败原因和降级方向完全不同。
 */

export interface PairCode {
  code: string;
  expiresInSeconds: number;
}

export interface PluginStatus {
  paired: boolean;
  installationId?: string;
  vaultName?: string;
  pairedAt?: string;
  lastActiveAt?: string;
}

export interface InstallResult {
  installed: boolean;
  alreadyInstalled: boolean;
  vaultPath?: string;
  vaultName?: string;
  installationId?: string;
  message: string;
}

export type TaskStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "PARTIAL_FAILED"
  | "FAILED"
  | "EXPIRED"
  | "SUPERSEDED";

export interface TaskProblem {
  path: string;
  status: string;
  error?: string;
}

export interface ImportTask {
  taskId: string;
  status: TaskStatus;
  total: number;
  processed: number;
  successCount: number;
  skippedCount: number;
  failedCount: number;
  conflictCount: number;
  message: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  expiredAt?: string;
  problems?: TaskProblem[];
}

export function createPairCode(): Promise<PairCode> {
  return request<PairCode>("/api/obsidian/plugin/pair-codes", { method: "POST" });
}

/**
 * 一键装插件：后端把 main.js / manifest.json / data.json（含 token）直接写进
 * 本机当前 Vault 的 .obsidian/plugins/memoagent/。装完只需重载 Obsidian。
 *
 * 后端在云端时返回 installed=false —— 服务器上没有 obsidian.json，
 * 这时改走 PLUGIN_PACKAGE_URL + pairLink()。
 */
export function installObsidianPlugin(): Promise<InstallResult> {
  return request<InstallResult>("/api/obsidian/plugin/install", { method: "POST" });
}

/** 插件包下载地址：交给 <a download> 用，不走 request 包装 */
export const PLUGIN_PACKAGE_URL = "/api/obsidian/plugin/package";

/**
 * obsidian:// 链接。插件注册了 memoagent-pair 协议处理器，
 * 用户点这个链接后 Obsidian 直接把参数交给插件完成配对，不用手抄 6 位码。
 */
export function pairLink(code: string): string {
  const host = window.location.origin;
  return `obsidian://memoagent-pair?host=${encodeURIComponent(host)}&code=${encodeURIComponent(code)}`;
}

export function fetchPluginStatus(): Promise<PluginStatus> {
  return request<PluginStatus>("/api/obsidian/plugin/status");
}

export function createImportTask(): Promise<ImportTask> {
  return request<ImportTask>("/api/obsidian/import-tasks", { method: "POST" });
}

/**
 * 订阅任务进度。后端推的是完整任务快照，收到直接渲染，不用再拉一次。
 * 连上后服务端会立即发一次当前状态，因此不需要先手动 GET。
 */
export function subscribeImportTask(
  taskId: string,
  onUpdate: (task: ImportTask) => void,
): () => void {
  const source = new EventSource(`/api/obsidian/import-tasks/${taskId}/stream`);
  source.onmessage = (event) => {
    try {
      onUpdate(JSON.parse(event.data) as ImportTask);
    } catch {
      // 心跳是注释帧，不会走到这里；解析失败说明是脏帧，忽略即可
    }
  };
  // onerror 不手动 close：EventSource 会按默认间隔自动重连
  return () => source.close();
}

/** 终态判断：到这些状态后不再期待后续推送 */
export function isFinished(status: TaskStatus): boolean {
  return (
    status === "COMPLETED" ||
    status === "PARTIAL_FAILED" ||
    status === "FAILED" ||
    status === "EXPIRED" ||
    status === "SUPERSEDED"
  );
}
