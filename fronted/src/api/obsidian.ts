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
  getDirectoryHandle(name: string, options?: { create?: boolean }): Promise<FsDirectoryHandle>;
  getFileHandle(name: string, options?: { create?: boolean }): Promise<FsFileHandle>;
}

type DirectoryPicker = (options?: { mode?: "read" | "readwrite" }) => Promise<FsDirectoryHandle>;

export function supportsDirectWrite(): boolean {
  return typeof (window as unknown as { showDirectoryPicker?: DirectoryPicker }).showDirectoryPicker === "function";
}

const HASH_KEY = "memoagent:obsidian:hashes";

function loadHashes(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(HASH_KEY) || "{}") as Record<string, string>;
  } catch {
    return {};
  }
}

/**
 * 让用户选一次 Vault 目录，然后把笔记直接写进去（会在其下建 MemoAgent/ 子目录，不动根目录其它文件）。
 *
 * 幂等：hash 与上次写入一致且文件仍在 → 跳过。跳过前会确认文件存在，
 * 因此在 Obsidian 里手工删掉某篇后再导出，它会被重新写回来。
 */
export async function writeNotesToVault(): Promise<{ written: number; skipped: number; total: number; folder: string }> {
  const picker = (window as unknown as { showDirectoryPicker?: DirectoryPicker }).showDirectoryPicker;
  if (!picker) {
    throw new Error("当前浏览器不支持直接写入目录，请改用「导出 Markdown 包」");
  }
  const root = await picker({ mode: "readwrite" });
  const notes = await fetchObsidianNotes();
  const hashes = loadHashes();
  let written = 0;
  let skipped = 0;

  for (const note of notes) {
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
  }

  localStorage.setItem(HASH_KEY, JSON.stringify(hashes));
  return { written, skipped, total: notes.length, folder: root.name };
}
