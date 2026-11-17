import { request } from "./http";

/** 导出前的统计：与后端 ObsidianPreviewVO 对齐 */
export interface ObsidianPreview {
  diaries: number;
  experiences: number;
  knowledge: number;
  total: number;
}

/** 一篇待写入的笔记：path 相对 Vault 根，hash 用于跳过未变化文件 */
export interface ObsidianNote {
  path: string;
  content: string;
  hash: string;
}

export function fetchObsidianPreview(): Promise<ObsidianPreview> {
  return request<ObsidianPreview>("/api/obsidian/preview");
}

export function fetchObsidianNotes(): Promise<ObsidianNote[]> {
  return request<ObsidianNote[]>("/api/obsidian/notes");
}

/* ---- 自动识别 Vault：后端读 obsidian.json，替前端拿到当前打开的目录 ---- */

export interface ObsidianVaultOption {
  path: string;
  name: string;
  current: boolean;
}

export interface ObsidianVault {
  found: boolean;
  path: string | null;
  name: string | null;
  configFile: string;
  message: string;
  candidates: ObsidianVaultOption[];
}

export interface ObsidianWriteResult {
  vaultPath: string;
  folder: string;
  written: number;
  skipped: number;
  failed: number;
  total: number;
  message: string;
}

/** 后端探测本机 Obsidian 当前打开的 Vault，找不到时 found=false 并带原因 */
export function fetchObsidianVault(): Promise<ObsidianVault> {
  return request<ObsidianVault>("/api/obsidian/vault");
}

/**
 * 让后端直接写进 Vault 下的 MemoAgent/。
 * vaultPath 留空 = 用自动识别的那个；传值必须是 obsidian.json 登记过的目录，否则后端拒绝。
 */
export function writeObsidianToVault(vaultPath?: string): Promise<ObsidianWriteResult> {
  return request<ObsidianWriteResult>("/api/obsidian/vault", {
    method: "POST",
    body: JSON.stringify({ vaultPath: vaultPath || null }),
  });
}

/**
 * 下载 zip 包。用 fetch + blob 而不是 <a download> 直连：后端异常时返回的是 JSON + 非 200，
 * 直连会把错误存成一个打不开的 zip，这里能正确抛出并提示。
 */
export async function downloadObsidianZip(): Promise<{ bytes: number }> {
  let response: Response;
  try {
    response = await fetch("/api/obsidian/export");
  } catch {
    throw new Error("无法连接后端，请确认服务已启动");
  }
  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `memoagent-obsidian-${new Date().toISOString().slice(0, 10)}.zip`;
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
  return { bytes: blob.size };
}

/* ---- File System Access API 的最小类型声明（lib.dom 尚未收录） ---- */

interface FsWritable {
  write(data: string): Promise<void>;
  close(): Promise<void>;
}

interface FsFileHandle {
  getFile(): Promise<File>;
  createWritable(): Promise<FsWritable>;
}

interface FsDirectoryHandle {
  name: string;
  queryPermission?(options?: { mode?: "read" | "readwrite" }): Promise<PermissionState>;
  requestPermission?(options?: { mode?: "read" | "readwrite" }): Promise<PermissionState>;
  getDirectoryHandle(name: string, options?: { create?: boolean }): Promise<FsDirectoryHandle>;
  getFileHandle(name: string, options?: { create?: boolean }): Promise<FsFileHandle>;
}

type DirectoryPicker = (options?: { mode?: "read" | "readwrite" }) => Promise<FsDirectoryHandle>;

export function supportsDirectWrite(): boolean {
  return typeof (window as unknown as { showDirectoryPicker?: DirectoryPicker }).showDirectoryPicker === "function";
}

const HASH_KEY = "memoagent:obsidian:hashes";
const DIR_DB = "memoagent-obsidian";
const DIR_STORE = "handles";
const DIR_KEY = "vault";

function loadHashes(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(HASH_KEY) || "{}") as Record<string, string>;
  } catch {
    return {};
  }
}

/* ---- 目录句柄持久化：存在 IndexedDB，下次不必重选 ---- */

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DIR_DB, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(DIR_STORE)) {
        db.createObjectStore(DIR_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function withStore<T>(mode: IDBTransactionMode, run: (store: IDBObjectStore) => IDBRequest): Promise<T | null> {
  try {
    const db = await openDb();
    return await new Promise<T | null>((resolve) => {
      const request = run(db.transaction(DIR_STORE, mode).objectStore(DIR_STORE));
      request.onsuccess = () => resolve((request.result as T) ?? null);
      request.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

function loadDirHandle(): Promise<FsDirectoryHandle | null> {
  return withStore<FsDirectoryHandle>("readonly", (store) => store.get(DIR_KEY));
}

function saveDirHandle(handle: FsDirectoryHandle): Promise<unknown> {
  return withStore("readwrite", (store) => store.put(handle, DIR_KEY));
}

/** 忘掉已记住的目录：下次写入会重新弹选择框 */
export async function forgetVaultDirectory(): Promise<void> {
  await withStore("readwrite", (store) => store.delete(DIR_KEY));
}

/** 已记住的目录名（仅用于界面展示「上次写入到哪」），没记住返回 null */
export async function rememberedVaultName(): Promise<string | null> {
  const handle = await loadDirHandle();
  return handle?.name ?? null;
}

/** 句柄还在，但浏览器可能已收回写权限：能续期就续期，不行就当作没记住 */
async function ensureWritable(handle: FsDirectoryHandle): Promise<boolean> {
  if (typeof handle.queryPermission !== "function") {
    return true;
  }
  const options = { mode: "readwrite" as const };
  try {
    if ((await handle.queryPermission(options)) === "granted") {
      return true;
    }
    if (typeof handle.requestPermission !== "function") {
      return false;
    }
    return (await handle.requestPermission(options)) === "granted";
  } catch {
    return false;
  }
}

export interface BrowserWriteResult {
  folder: string;
  written: number;
  skipped: number;
  failed: number;
  total: number;
  /** true = 复用了已记住的目录，本次没有弹选择框 */
  reused: boolean;
}

/**
 * 把笔记写进**用户自己电脑上**的 Obsidian Vault。
 *
 * 数据先由后端渲染成 JSON（GET /api/obsidian/notes），落盘完全在浏览器里完成，
 * 因此后端部署在本机还是云端都不影响——只要浏览器能访问后端即可。
 * 目录句柄存在 IndexedDB，第二次起不再弹选择框，仅在权限过期时请求一次续期。
 *
 * 幂等：hash 与上次写入一致且文件仍在 → 跳过。跳过前会确认文件存在，
 * 因此在 Obsidian 里手工删掉某篇后再导出，它会被重新写回来。
 */
export async function writeNotesToVault(options?: { repick?: boolean }): Promise<BrowserWriteResult> {
  const picker = (window as unknown as { showDirectoryPicker?: DirectoryPicker }).showDirectoryPicker;

  let root: FsDirectoryHandle | null = options?.repick ? null : await loadDirHandle();
  let reused = false;
  if (root) {
    if (await ensureWritable(root)) {
      reused = true;
    } else {
      root = null;
    }
  }
  if (!root) {
    if (!picker) {
      throw new Error("当前浏览器不支持直接写入目录，请改用「导出 Markdown 包」");
    }
    root = await picker({ mode: "readwrite" });
    await saveDirHandle(root);
  }

  const notes = await fetchObsidianNotes();
  const hashes = loadHashes();
  let written = 0;
  let skipped = 0;
  let failed = 0;

  for (const note of notes) {
    try {
      const segments = note.path.split("/");
      const fileName = segments.pop() || "note.md";
      let dir = root;
      for (const segment of segments) {
        dir = await dir.getDirectoryHandle(segment, { create: true });
      }

      if (hashes[note.path] === note.hash) {
        const exists = await dir.getFileHandle(fileName).then(
          () => true,
          () => false,
        );
        if (exists) {
          skipped++;
          continue;
        }
      }

      const handle = await dir.getFileHandle(fileName, { create: true });
      const writable = await handle.createWritable();
      await writable.write(note.content);
      await writable.close();
      hashes[note.path] = note.hash;
      written++;
    } catch (error) {
      const name = (error as DOMException)?.name;
      // 目录被移动/删除，或权限被收回：丢掉记住的句柄，重新选一次目录再来
      if (reused && (name === "NotAllowedError" || name === "NotFoundError")) {
        await forgetVaultDirectory();
        return writeNotesToVault({ repick: true });
      }
      failed++;
    }
  }

  localStorage.setItem(HASH_KEY, JSON.stringify(hashes));
  return { folder: root.name, written, skipped, failed, total: notes.length, reused };
}
