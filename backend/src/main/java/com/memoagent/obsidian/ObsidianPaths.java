package com.memoagent.obsidian;

import com.memoagent.exception.BusinessException;

/**
 * Vault 内的路径规则，前后端与插件共用同一套判断。
 *
 * 任务里的 path 由渲染器产出，理论上安全；但 DELETE 类路径来自数据库快照，
 * 一旦脏数据进来就可能变成「写到 Vault 之外」。所以在生成任务、插件落盘两处都要校验。
 */
public final class ObsidianPaths {

    /** 所有笔记都写在这个子目录下，不碰 Vault 根目录的其它文件 */
    public static final String ROOT = "MemoAgent";

    private ObsidianPaths() {
    }

    /** 必须是 MemoAgent/ 开头、用正斜杠、不含 .. 与反斜杠，且以 .md 结尾 */
    public static boolean isSafe(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (!path.startsWith(ROOT + "/") || !path.endsWith(".md")) {
            return false;
        }
        return !path.contains("..") && !path.contains("\\") && !path.contains("//");
    }

    /** 不安全就抛 400，避免脏数据一路传到插件 */
    public static String requireSafe(String path) {
        if (!isSafe(path)) {
            throw BusinessException.badRequest("笔记路径不合法：" + path);
        }
        return path;
    }
}
