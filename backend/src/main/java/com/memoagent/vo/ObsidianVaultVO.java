package com.memoagent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Vault 自动识别结果。
 *
 * found=false 时前端退回「手动选目录」，message 里是具体原因（没装 Obsidian / 没有登记 Vault / JSON 坏了）。
 */
@Data
public class ObsidianVaultVO {

    private boolean found;

    /** 首选 Vault 的绝对路径（写入目标 = 该路径下的 MemoAgent/） */
    private String path;

    /** Vault 目录名，用于界面显示 */
    private String name;

    /** 探测所用的配置文件，排查「识别到的不是我想要的 Vault」时有用 */
    private String configFile;

    private String message;

    /** 全部候选，按当前打开 / 最近使用排序 */
    private List<Option> candidates = new ArrayList<>();

    @Data
    public static class Option {

        private String path;

        private String name;

        /** 是否是 Obsidian 当前打开的 Vault */
        private boolean current;
    }
}
