package com.memoagent.obsidian;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 统一的摘要工具。后端算出来的 hash 必须和 Obsidian 插件算的完全一致，
 * 否则每次导入都会误判「本地被修改」，全量重写一遍。
 *
 * 约定（插件侧必须照此实现）：
 * 1. 先做换行归一化：\r\n 与 \r 一律视为 \n，再按 UTF-8 取字节；
 * 2. 内容指纹 = SHA-256 前 8 字节，转 16 位小写十六进制。
 */
public final class Digests {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Digests() {
    }

    /** 全文 SHA-256，64 位小写十六进制。用于 token 落库与 path 唯一键。 */
    public static String sha256(String text) {
        return hex(digest(text), 32);
    }

    /** 内容指纹：SHA-256 前 8 字节 = 16 位小写十六进制。与导出接口的 hash 字段一致。 */
    public static String contentHash(String text) {
        return hex(digest(normalize(text)), 8);
    }

    /** 换行归一化：CRLF / CR → LF。文件在 Windows 编辑器里改过会变成 CRLF，不归一化会一直误判为改动。 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            // 标准 JDK 必带 SHA-256，走到这里说明运行环境异常，不能静默降级成弱哈希
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String hex(byte[] bytes, int count) {
        StringBuilder builder = new StringBuilder(count * 2);
        for (int index = 0; index < count; index++) {
            builder.append(HEX[(bytes[index] >> 4) & 0x0F]);
            builder.append(HEX[bytes[index] & 0x0F]);
        }
        return builder.toString();
    }
}
