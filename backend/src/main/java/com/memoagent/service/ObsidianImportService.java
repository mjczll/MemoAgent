package com.memoagent.service;

import com.memoagent.dto.ObsidianCompleteRequest;
import com.memoagent.dto.ObsidianPairRequest;
import com.memoagent.dto.ObsidianProgressRequest;
import com.memoagent.vo.ObsidianImportTaskVO;
import com.memoagent.vo.ObsidianInstallResultVO;
import com.memoagent.vo.ObsidianPairCodeVO;
import com.memoagent.vo.ObsidianPairResultVO;
import com.memoagent.vo.ObsidianPluginTaskVO;
import com.memoagent.vo.ObsidianPluginVO;

/**
 * Obsidian 插件式导入：Web 建任务，插件领任务并写入本地 Vault，进度经 SSE 回推。
 *
 * 与原来的「后端直写」「浏览器 FSA 直写」并列，是三级降级里的第一优先：
 * 插件在场时用户不需要选目录，也不要求后端与 Obsidian 同机。
 */
public interface ObsidianImportService {

    // ---- 配对（Web 与插件） ----

    /** Web 生成 6 位配对码，10 分钟有效、只能消费一次 */
    ObsidianPairCodeVO createPairCode();

    /** 插件提交配对码换长期 token（明文只返回这一次） */
    ObsidianPairResultVO pair(ObsidianPairRequest request);

    /** Web 查绑定状态 */
    ObsidianPluginVO pluginStatus();

    /** 插件自查（顺带刷新 lastActiveAt） */
    ObsidianPluginVO me();

    // ---- 插件安装 ----

    /**
     * 把插件直接装进本机的当前 Vault，配置一并写好（含 token），用户只需重载 Obsidian。
     *
     * 仅当后端与 Obsidian 同机时可行；后端在云端时返回 installed=false，
     * 前端据此改走「下载插件包 + obsidian:// 连接链接」。
     *
     * @param serverUrl 插件要访问的地址，取当前请求的 origin（云端时就是用户访问的域名）
     */
    ObsidianInstallResultVO installLocal(String serverUrl);

    /** 插件包 zip：memoagent/ 文件夹 + 安装说明，任何浏览器都能下载 */
    byte[] pluginPackage();

    // ---- 任务（Web 侧） ----

    /** 按当前资产生成导入任务；同用户遗留的未完成任务置 SUPERSEDED */
    ObsidianImportTaskVO createTask();

    ObsidianImportTaskVO task(String taskId);

    // ---- 任务（插件侧） ----

    /** 领取待处理任务；没有则返回 null。PENDING 首次领取时转 RUNNING。 */
    ObsidianPluginTaskVO pendingTask();

    void progress(String taskId, ObsidianProgressRequest request);

    void complete(String taskId, ObsidianCompleteRequest request);
}
