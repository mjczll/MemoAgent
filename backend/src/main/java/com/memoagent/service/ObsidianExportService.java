package com.memoagent.service;

import com.memoagent.obsidian.RenderedNote;
import com.memoagent.vo.ObsidianNoteVO;
import com.memoagent.vo.ObsidianPreviewVO;
import com.memoagent.vo.ObsidianVaultVO;
import com.memoagent.vo.ObsidianWriteResultVO;

import java.util.List;

/**
 * Obsidian 单向导出：把日记 / 经验 / 知识渲染成 Markdown 笔记集合。
 *
 * 三种取用方式共用同一份渲染结果：zip 下载、返回 JSON 由浏览器写盘、
 * 或由后端直接写进本机 Obsidian 当前打开的 Vault。
 */
public interface ObsidianExportService {

    ObsidianPreviewVO preview();

    /** 全部笔记（含 README），路径相对 Vault 根。 */
    List<ObsidianNoteVO> notes();

    /**
     * 全部笔记的渲染结果，额外带 assetType / assetId。
     *
     * 导入任务需要知道每篇笔记对应哪条资产，才能在下次导入时算出 DELETE；
     * 内容本身与 {@link #notes()} 完全相同（同一份渲染，不重复实现）。
     */
    List<RenderedNote> render();

    /** 打成 zip（UTF-8 文件名），供浏览器直接下载。 */
    byte[] zip();

    /** 读 obsidian.json 找出当前打开的 Vault。 */
    ObsidianVaultVO vault();

    /**
     * 直接写进 Vault 下的 MemoAgent/ 子目录，内容未变的文件跳过。
     *
     * @param vaultPath 为空时用自动识别的当前 Vault；非空时必须是 obsidian.json 登记过的目录
     */
    ObsidianWriteResultVO writeToVault(String vaultPath);
}
