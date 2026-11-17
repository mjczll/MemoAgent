package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.ObsidianCompleteRequest;
import com.memoagent.dto.ObsidianPairRequest;
import com.memoagent.dto.ObsidianProgressRequest;
import com.memoagent.obsidian.ObsidianTaskWaiter;
import com.memoagent.obsidian.PluginContext;
import com.memoagent.service.ObsidianImportService;
import com.memoagent.vo.ObsidianPairResultVO;
import com.memoagent.vo.ObsidianPluginTaskVO;
import com.memoagent.vo.ObsidianPluginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * Obsidian 插件专用接口。
 *
 * 除 /pair 外都要求 X-Plugin-Token（由 PluginTokenInterceptor 校验），
 * 插件只认自己绑定的那个安装实例的任务，不会读到别的实例或别的用户的任务。
 */
@RestController
@RequestMapping("/api/obsidian/plugin")
@RequiredArgsConstructor
public class ObsidianPluginController {

    private final ObsidianImportService obsidianImportService;
    private final ObsidianTaskWaiter taskWaiter;

    /** 用配对码换长期 token。明文 token 只在这个响应里出现一次。 */
    @PostMapping("/pair")
    public Result<ObsidianPairResultVO> pair(@Valid @RequestBody ObsidianPairRequest request) {
        return Result.ok(obsidianImportService.pair(request));
    }

    /** 插件自检：token 是否有效、绑定的 Vault 是哪个 */
    @GetMapping("/me")
    public Result<ObsidianPluginVO> me() {
        return Result.ok(obsidianImportService.me());
    }

    /** 领任务。data 为 null 表示当前没有待处理任务。 */
    @GetMapping("/tasks/pending")
    public Result<ObsidianPluginTaskVO> pending() {
        return Result.ok(obsidianImportService.pendingTask());
    }

    /**
     * 长轮询领任务：有任务立刻返回；没有就挂起，最多 {@code timeout} 秒。
     *
     * 网页一建任务，{@link ObsidianTaskWaiter#signal} 就会把这些挂着的请求放行（返回 data=null），
     * 插件收到 null 立刻重发，那一次查询就能拿到任务——所以用户点完按钮几乎是即时开始，
     * 空闲时又只占一条连接、20 秒才一个请求。
     */
    @GetMapping("/tasks/wait")
    public DeferredResult<Result<ObsidianPluginTaskVO>> wait(
            @RequestParam(name = "timeout", defaultValue = "20") int timeout) {

        // 先查一次：任务已经在等着了就没必要挂起
        ObsidianPluginTaskVO task = obsidianImportService.pendingTask();
        if (task != null) {
            DeferredResult<Result<ObsidianPluginTaskVO>> immediate = new DeferredResult<>();
            immediate.setResult(Result.ok(task));
            return immediate;
        }

        // 上限 28 秒：给 Nginx 默认 60 秒读超时留足余量，避免代理先把连接掐了
        int seconds = Math.max(5, Math.min(timeout, 28));
        return taskWaiter.await(PluginContext.get().getUserId(), seconds * 1000L);
    }

    /** 进度上报：每处理 10 篇左右报一次即可 */
    @PostMapping("/tasks/{taskId}/progress")
    public Result<Void> progress(@PathVariable String taskId,
                                 @RequestBody ObsidianProgressRequest request) {
        obsidianImportService.progress(taskId, request);
        return Result.ok(null);
    }

    /** 完成上报：带逐篇结果，服务端据此更新同步快照（删除同步与冲突判定的依据） */
    @PostMapping("/tasks/{taskId}/complete")
    public Result<Void> complete(@PathVariable String taskId,
                                 @Valid @RequestBody ObsidianCompleteRequest request) {
        obsidianImportService.complete(taskId, request);
        return Result.ok(null);
    }
}
