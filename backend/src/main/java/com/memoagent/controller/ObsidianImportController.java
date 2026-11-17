package com.memoagent.controller;

import com.memoagent.common.CurrentUser;
import com.memoagent.common.Result;
import com.memoagent.obsidian.ObsidianTaskWaiter;
import com.memoagent.service.ObsidianImportService;
import com.memoagent.service.ObsidianImportStreamService;
import com.memoagent.vo.ObsidianImportTaskVO;
import com.memoagent.vo.ObsidianInstallResultVO;
import com.memoagent.vo.ObsidianPairCodeVO;
import com.memoagent.vo.ObsidianPluginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 插件式导入的 Web 侧接口：装插件、生成配对码、查绑定状态、建任务、看进度。
 *
 * 插件的接口在 {@link ObsidianPluginController}，那边要 X-Plugin-Token，
 * 这里一条都不需要——浏览器本来就是「没有凭证也能看自己页面」的角色。
 */
@RestController
@RequestMapping("/api/obsidian")
@RequiredArgsConstructor
public class ObsidianImportController {

    private final ObsidianImportService obsidianImportService;
    private final ObsidianImportStreamService streamService;
    private final ObsidianTaskWaiter taskWaiter;

    /**
     * 一键把插件装进本机的当前 Vault（含 token，装完重载 Obsidian 即可用）。
     *
     * 后端在云端时本机没有 Vault，返回 installed=false —— 前端据此改走
     * 「下载插件包 + obsidian:// 连接链接」。两条路最终落成同一份配置。
     */
    @PostMapping("/plugin/install")
    public Result<ObsidianInstallResultVO> installPlugin() {
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return Result.ok(obsidianImportService.installLocal(origin));
    }

    /** 插件包下载：memoagent/ 文件夹 + 安装说明，解压拖进 .obsidian/plugins/ 即可 */
    @GetMapping("/plugin/package")
    public ResponseEntity<byte[]> pluginPackage() {
        byte[] payload = obsidianImportService.pluginPackage();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"memoagent-obsidian-plugin.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(payload.length)
                .body(payload);
    }

    /** 生成 6 位配对码，10 分钟内有效、只能消费一次 */
    @PostMapping("/plugin/pair-codes")
    public Result<ObsidianPairCodeVO> createPairCode() {
        return Result.ok(obsidianImportService.createPairCode());
    }

    /** 是否已连接插件、绑定的是哪个 Vault */
    @GetMapping("/plugin/status")
    public Result<ObsidianPluginVO> pluginStatus() {
        return Result.ok(obsidianImportService.pluginStatus());
    }

    /** 建导入任务：同用户遗留的未完成任务会被置 SUPERSEDED */
    @PostMapping("/import-tasks")
    public Result<ObsidianImportTaskVO> createTask() {
        ObsidianImportTaskVO task = obsidianImportService.createTask();
        // 事务在 service 方法返回时就已提交，此刻放行长轮询是安全的：
        // 插件被唤醒后重发的查询一定看得到这条任务。放在 service 里做会在提交前唤醒，白等一轮。
        taskWaiter.signal(CurrentUser.id());
        return Result.ok(task);
    }

    /** 任务当前状态（首屏拉一次；之后靠 SSE 推送） */
    @GetMapping("/import-tasks/{taskId}")
    public Result<ObsidianImportTaskVO> task(@PathVariable String taskId) {
        return Result.ok(obsidianImportService.task(taskId));
    }

    /**
     * 进度推送。连上先发一次当前状态，避免「任务已经跑起来但页面还不知道」。
     * 推送体是完整任务快照，前端直接渲染，不用再拉一次接口。
     */
    @GetMapping("/import-tasks/{taskId}/stream")
    public SseEmitter stream(@PathVariable String taskId) {
        SseEmitter emitter = streamService.subscribe(taskId);
        try {
            streamService.publish(taskId, obsidianImportService.task(taskId));
        } catch (RuntimeException exception) {
            // 任务号不存在时不能把 emitter 挂在列表里空等心跳，直接让这次连接结束
            emitter.completeWithError(exception);
        }
        return emitter;
    }
}
