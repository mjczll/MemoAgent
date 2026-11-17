# MemoAgent Obsidian 插件

这个目录只放说明。**插件的实际源码在 `backend/src/main/resources/obsidian-plugin/`**，随后端 jar 一起发布。

## 为什么这里没有 TypeScript 工程

插件只用到 Obsidian 的公开 API 和 Node 内置的 `crypto`，没有任何第三方依赖，本来就不需要打包。
所以 `main.js` 直接手写成 CommonJS，和 `manifest.json` 一起放在 resources 下：

- 用户不需要 `npm install` / `npm run build`；
- 后端可以直接把它写进本机 Vault（同机部署时一键安装）；
- 后端也可以把它打成 zip 给云端用户下载。

只有一份实现，不存在「源码改了但产物忘了重新构建」这种错位。

## 两种安装方式（用户侧，都不涉及命令行）

| 部署形态 | 用户操作 |
| --- | --- |
| 后端与 Obsidian 同机 | 设置页点「连接 Obsidian」→ 后端写入插件与配置 → 重载 Obsidian |
| 后端在云端 | 设置页点「连接 Obsidian」→ 下载插件包 → 解压放进 `.obsidian/plugins/` → 点页面上的连接链接 |

两条路的终点相同：`.obsidian/plugins/memoagent/data.json` 里有一份可用的 token。
之后点「一键导入 Obsidian」，两种部署下的行为完全一致。

## 使用

网页上点「一键导入 Obsidian」。插件平时挂一个长轮询（`/plugin/tasks/wait`），
网页一建任务服务端就把挂起的请求放行，插件重发一次就拿到任务，几乎即时开始写盘；
闲着的时候 20 秒才一个请求，不浪费连接。
网页上的进度条通过 SSE 实时更新。即使不点网页，只要 Obsidian 开着，网页端建的任务也会立即开始。

## 冲突规则

任务里每篇笔记都带两个指纹：远端当前内容 `hash`、上次同步成功时的 `lastSyncedHash`。
插件按这个表处理，**不会静默覆盖你在 Obsidian 里改过的内容**：

| 本地 | 远端 | 处理 |
|---|---|---|
| 与远端内容相同 | — | 跳过，不碰文件（不触发重索引） |
| 没有基线（首次同步） | 有内容 | 覆盖。你点了导入，就以 MemoAgent 为准 |
| 本地 hash = 基线 | 变了 | 覆盖（本地没动过，安全更新） |
| 本地 hash ≠ 基线 | 没变 | 跳过，保留你的改动 |
| 本地 hash ≠ 基线 | 也变了 | 标为冲突，保留本地文件，网页上能看到路径 |
| — | 删除 | 移入 Obsidian 回收站；本地改过则不删，标冲突 |

## 其它边界

- **桌面端专用**（`isDesktopOnly: true`）：要用 Node 的 `crypto` 算指纹，且移动端插件写盘能力受限。
- **只写 `MemoAgent/`**：路径必须以 `MemoAgent/` 开头、不含 `..`，否则整篇拒绝。
- **串行写入**：200 篇一批大约十几秒，期间 Obsidian 可能略卡——这是为了避免并发写把索引打乱。
- **幂等**：同步到一半关掉 Obsidian，下次启动会继续领同一个任务，已写好的文件按内容判等跳过。
- **单向**：在 Obsidian 里新建的笔记不会被 MemoAgent 读到，再次导入也不会删掉它们。

## 调试

改完 `backend/src/main/resources/obsidian-plugin/main.js` 后重启后端，在设置页重新点一次「连接 Obsidian」，
后端会把新文件覆盖进 Vault（`writeTo` 是原地覆盖），再重载 Obsidian 即生效。

Obsidian 里按 `Ctrl+Shift+I` 打开开发者工具可以看插件日志。
