package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.ObsidianExportService;
import com.memoagent.vo.ObsidianNoteVO;
import com.memoagent.vo.ObsidianPreviewVO;
import com.memoagent.vo.ObsidianVaultVO;
import com.memoagent.vo.ObsidianWriteRequest;
import com.memoagent.vo.ObsidianWriteResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Obsidian 单向导出。四种取用方式，共用同一份渲染结果：
 * - preview：导前统计
 * - notes：JSON 全量文本，供浏览器用 File System Access API 写入用户手选的目录
 * - export：zip 流，任何浏览器都能下载
 * - vault：读 obsidian.json 自动识别当前 Vault；POST 同一路径则直接写进该 Vault 的 MemoAgent/
 *
 * 只导出不导入：从 Obsidian 回流到 MemoAgent 不在计划内。
 */
@RestController
@RequestMapping("/api/obsidian")
@RequiredArgsConstructor
public class ObsidianController {

    private final ObsidianExportService obsidianExportService;

    @GetMapping("/preview")
    public Result<ObsidianPreviewVO> preview() {
        return Result.ok(obsidianExportService.preview());
    }

    @GetMapping("/notes")
    public Result<List<ObsidianNoteVO>> notes() {
        return Result.ok(obsidianExportService.notes());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] payload = obsidianExportService.zip();
        String fileName = "memoagent-obsidian-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(payload.length)
                .body(payload);
    }

    /** 自动识别：本机 Obsidian 当前打开的是哪个 Vault。 */
    @GetMapping("/vault")
    public Result<ObsidianVaultVO> vault() {
        return Result.ok(obsidianExportService.vault());
    }

    /** 直接写入：不传 vaultPath 就用自动识别的那个；传了必须是 obsidian.json 登记过的目录。 */
    @PostMapping("/vault")
    public Result<ObsidianWriteResultVO> writeToVault(@RequestBody(required = false) ObsidianWriteRequest request) {
        return Result.ok(obsidianExportService.writeToVault(request == null ? null : request.getVaultPath()));
    }
}
