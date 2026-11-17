package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoagent.common.CurrentUser;
import com.memoagent.dto.ObsidianCompleteRequest;
import com.memoagent.dto.ObsidianPairRequest;
import com.memoagent.dto.ObsidianProgressRequest;
import com.memoagent.entity.ObsidianImportTask;
import com.memoagent.entity.ObsidianImportTaskFile;
import com.memoagent.entity.ObsidianPlugin;
import com.memoagent.entity.ObsidianSyncState;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.ObsidianImportTaskFileMapper;
import com.memoagent.mapper.ObsidianImportTaskMapper;
import com.memoagent.mapper.ObsidianPluginMapper;
import com.memoagent.mapper.ObsidianSyncStateMapper;
import com.memoagent.obsidian.Digests;
import com.memoagent.obsidian.ObsidianPaths;
import com.memoagent.obsidian.ObsidianPluginPackage;
import com.memoagent.obsidian.ObsidianVaultLocator;
import com.memoagent.obsidian.PluginContext;
import com.memoagent.obsidian.RenderedNote;
import com.memoagent.service.ObsidianExportService;
import com.memoagent.service.ObsidianImportService;
import com.memoagent.service.ObsidianImportStreamService;
import com.memoagent.vo.ObsidianFileResultVO;
import com.memoagent.vo.ObsidianImportTaskVO;
import com.memoagent.vo.ObsidianInstallResultVO;
import com.memoagent.vo.ObsidianPairCodeVO;
import com.memoagent.vo.ObsidianPairResultVO;
import com.memoagent.vo.ObsidianPluginTaskVO;
import com.memoagent.vo.ObsidianPluginVO;
import com.memoagent.vo.ObsidianTaskFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件式导入的实现。
 *
 * 三条关键设计：
 * 1. 任务里放的是**渲染快照**，不是资产 id：任务执行期间资产再被编辑，也不会写到一半新一半旧。
 * 2. 删除靠 {@code obsidian_sync_state} 反推：本次渲染没有、快照里有的路径就是 DELETE。
 *    没有这张表就只知道"现在有什么"，删掉的资产会永远留在 Vault 里。
 * 3. 同步快照只在插件明确回报成功后更新。没收到逐篇结果时，宁可下次多写一遍也不要把失败误记为已同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObsidianImportServiceImpl implements ObsidianImportService {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL_FAILED = "PARTIAL_FAILED";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";
    public static final String SUPERSEDED = "SUPERSEDED";

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    public static final String FILE_SUCCESS = "SUCCESS";
    public static final String FILE_SKIPPED = "SKIPPED";
    public static final String FILE_FAILED = "FAILED";
    public static final String FILE_CONFLICT = "CONFLICT";
    public static final String FILE_PENDING = "PENDING";

    /** 配对码有效期：够用户从网页切到 Obsidian 输完 */
    private static final Duration PAIR_TTL = Duration.ofMinutes(10);

    /** 任务有效期：够用户"同步到一半关掉 Obsidian、第二天再打开继续" */
    private static final Duration TASK_TTL = Duration.ofHours(24);

    private static final int PAIR_CODE_BOUND = 1_000_000;
    private static final int TOKEN_BYTES = 32;
    private static final int ERROR_MAX = 500;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 配对码放内存：单实例部署够用，服务重启后重新生成即可。
     * 将来多实例部署时换成 Redis（TTL 直接由 Redis 承担）。
     */
    private final Map<String, PairCode> pairCodes = new ConcurrentHashMap<>();

    private final ObsidianPluginMapper pluginMapper;
    private final ObsidianImportTaskMapper taskMapper;
    private final ObsidianImportTaskFileMapper fileMapper;
    private final ObsidianSyncStateMapper syncStateMapper;
    private final ObsidianExportService obsidianExportService;
    private final ObsidianImportStreamService streamService;
    private final ObsidianPluginPackage pluginBundle;
    private final ObjectMapper objectMapper;

    private record PairCode(long userId, LocalDateTime expiresAt) {
    }

    // ==================== 插件安装 ====================

    /**
     * 一键装进本机 Vault。
     *
     * 与「配对码」是两条入口，殊途同归——最终都落成 .obsidian/plugins/memoagent/data.json 里的一个 token。
     * 区别只在于：同机时后端自己能写盘，就没必要让用户抄 6 位码。
     */
    @Override
    @Transactional
    public ObsidianInstallResultVO installLocal(String serverUrl) {
        ObsidianInstallResultVO result = new ObsidianInstallResultVO();

        ObsidianVaultLocator.Result located = ObsidianVaultLocator.locate();
        ObsidianVaultLocator.Option current = located.current();
        if (current == null) {
            // 云端部署的常态：服务器上没有 obsidian.json。交给前端走下载包那条路
            result.setInstalled(false);
            result.setMessage(located.message());
            return result;
        }

        Path vault = Path.of(current.path());
        Path pluginDir = vault.resolve(".obsidian").resolve("plugins").resolve(ObsidianPluginPackage.FOLDER);
        boolean existed = Files.isDirectory(pluginDir);

        Long userId = CurrentUser.id();
        // 复用这台机器上一次的记录：重装只是换 token，不该在库里堆废行
        ObsidianPlugin plugin = pluginMapper.selectOne(Wrappers.<ObsidianPlugin>lambdaQuery()
                .eq(ObsidianPlugin::getUserId, userId)
                .eq(ObsidianPlugin::getRevoked, 0)
                .orderByDesc(ObsidianPlugin::getLastActiveAt)
                .last("limit 1"));

        String token = randomToken();
        LocalDateTime now = LocalDateTime.now();
        if (plugin == null) {
            plugin = new ObsidianPlugin();
            plugin.setUserId(userId);
            plugin.setInstallationId(UUID.randomUUID().toString());
            plugin.setVaultName(current.name());
            plugin.setTokenHash(Digests.sha256(token));
            plugin.setTokenHint(hint(token));
            plugin.setCreatedAt(now);
            plugin.setLastActiveAt(now);
            plugin.setRevoked(0);
            pluginMapper.insert(plugin);
        } else {
            plugin.setVaultName(current.name());
            plugin.setTokenHash(Digests.sha256(token));
            plugin.setTokenHint(hint(token));
            plugin.setRevoked(0);
            plugin.setLastActiveAt(now);
            pluginMapper.updateById(plugin);
        }

        try {
            pluginBundle.writeTo(pluginDir);
            writePluginData(pluginDir, serverUrl, token, plugin.getInstallationId(), current.name());
            enablePlugin(vault);
        } catch (IOException exception) {
            log.error("写 Obsidian 插件失败：{}", pluginDir, exception);
            throw BusinessException.badRequest("无法写入 Vault 插件目录：" + exception.getMessage());
        }

        result.setInstalled(true);
        result.setAlreadyInstalled(existed);
        result.setVaultPath(vault.toString());
        result.setVaultName(current.name());
        result.setInstallationId(plugin.getInstallationId());
        result.setMessage(existed ? "插件已更新，重载 Obsidian 后生效" : "插件已装好，重载 Obsidian 后生效");
        return result;
    }

    @Override
    public byte[] pluginPackage() {
        return pluginBundle.zip();
    }

    /** 插件启动时 loadData() 读的就是这个文件，字段名必须和 DEFAULT_SETTINGS 对齐。 */
    private void writePluginData(Path pluginDir, String serverUrl, String token,
                                 String installationId, String vaultName) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverUrl", serverUrl);
        data.put("token", token);
        data.put("installationId", installationId);
        data.put("vaultName", vaultName);
        data.put("pairCodeInput", "");
        Files.writeString(pluginDir.resolve("data.json"),
                objectMapper.writeValueAsString(data), StandardCharsets.UTF_8);
    }

    /**
     * 把插件加进 .obsidian/community-plugins.json（就是「第三方插件已启用」那个列表）。
     * 不改这个文件，用户重载后插件仍是关着的，等于没装。
     */
    private void enablePlugin(Path vault) throws IOException {
        Path file = vault.resolve(".obsidian").resolve("community-plugins.json");
        List<String> ids = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!raw.isEmpty()) {
                try {
                    ids = objectMapper.readValue(raw, new TypeReference<List<String>>() {
                    });
                } catch (IOException broken) {
                    // 手工改坏时重建：装上插件比保留坏配置重要
                    log.warn("community-plugins.json 解析失败，将重建：{}", file);
                    ids = new ArrayList<>();
                }
            }
        }
        if (ids.contains(ObsidianPluginPackage.FOLDER)) {
            return;
        }
        ids.add(ObsidianPluginPackage.FOLDER);
        Files.createDirectories(file.getParent());
        Files.writeString(file, objectMapper.writeValueAsString(ids), StandardCharsets.UTF_8);
    }

    // ==================== 配对 ====================

    @Override
    public ObsidianPairCodeVO createPairCode() {
        LocalDateTime now = LocalDateTime.now();
        pairCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));

        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(PAIR_CODE_BOUND));
        } while (pairCodes.containsKey(code));

        pairCodes.put(code, new PairCode(CurrentUser.id(), now.plus(PAIR_TTL)));

        ObsidianPairCodeVO vo = new ObsidianPairCodeVO();
        vo.setCode(code);
        vo.setExpiresInSeconds((int) PAIR_TTL.toSeconds());
        return vo;
    }

    @Override
    @Transactional
    public ObsidianPairResultVO pair(ObsidianPairRequest request) {
        String code = request.getCode().trim();
        // 一次性消费：无论后续校验是否通过都作废，避免同一个码被反复试
        PairCode issued = pairCodes.remove(code);
        if (issued == null || issued.expiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("配对码已失效，请在设置页重新生成");
        }

        String token = randomToken();
        String tokenHash = Digests.sha256(token);
        LocalDateTime now = LocalDateTime.now();

        ObsidianPlugin plugin = pluginMapper.selectOne(Wrappers.<ObsidianPlugin>lambdaQuery()
                .eq(ObsidianPlugin::getInstallationId, request.getInstallationId().trim())
                .last("limit 1"));
        if (plugin == null) {
            plugin = new ObsidianPlugin();
            plugin.setUserId(issued.userId());
            plugin.setInstallationId(request.getInstallationId().trim());
            plugin.setVaultName(request.getVaultName().trim());
            plugin.setTokenHash(tokenHash);
            plugin.setTokenHint(hint(token));
            plugin.setCreatedAt(now);
            plugin.setLastActiveAt(now);
            plugin.setRevoked(0);
            pluginMapper.insert(plugin);
        } else {
            // 同一个安装重新配对：换 token，其余绑定的 Vault 一并更新
            plugin.setVaultName(request.getVaultName().trim());
            plugin.setTokenHash(tokenHash);
            plugin.setTokenHint(hint(token));
            plugin.setRevoked(0);
            plugin.setLastActiveAt(now);
            pluginMapper.updateById(plugin);
        }

        ObsidianPairResultVO result = new ObsidianPairResultVO();
        result.setPluginToken(token);
        result.setInstallationId(plugin.getInstallationId());
        result.setVaultName(plugin.getVaultName());
        return result;
    }

    @Override
    public ObsidianPluginVO pluginStatus() {
        return toPluginVO(findPlugin(CurrentUser.id()));
    }

    @Override
    public ObsidianPluginVO me() {
        ObsidianPlugin plugin = PluginContext.get();
        LocalDateTime now = LocalDateTime.now();
        // 节流：插件每 8 秒轮询一次，没必要每次都写库
        if (plugin.getLastActiveAt() == null || plugin.getLastActiveAt().isBefore(now.minusMinutes(1))) {
            plugin.setLastActiveAt(now);
            pluginMapper.updateById(plugin);
        }
        return toPluginVO(plugin);
    }

    // ==================== 建任务（Web） ====================

    @Override
    @Transactional
    public ObsidianImportTaskVO createTask() {
        Long userId = CurrentUser.id();
        ObsidianPlugin plugin = findPlugin(userId);
        if (plugin == null) {
            throw BusinessException.badRequest("尚未连接 Obsidian 插件，请先完成配对");
        }
        if (!StringUtils.hasText(plugin.getVaultName())) {
            throw BusinessException.badRequest("插件还没有绑定 Vault，请在 Obsidian 里重新配对");
        }

        expireStale(userId);
        supersedeOpen(userId, plugin.getInstallationId());

        Map<String, ObsidianSyncState> states = loadStates(plugin.getInstallationId());
        List<RenderedNote> rendered = obsidianExportService.render();

        List<ObsidianImportTaskFile> files = new ArrayList<>();
        Set<String> current = new HashSet<>();
        for (RenderedNote note : rendered) {
            // README 是 zip / 直写用的说明页，插件导入不写它，否则进度总数和"9 篇"对不上
            if ("readme".equals(note.assetType())) {
                continue;
            }
            ObsidianPaths.requireSafe(note.path());
            current.add(note.path());
            files.add(buildFile(note, states.get(note.path())));
        }

        // 快照里有、这次渲染没有：资产被删了，或标题变了导致文件名变了（旧文件要清走）
        for (ObsidianSyncState state : states.values()) {
            if (current.contains(state.getPath())) {
                continue;
            }
            if (!ObsidianPaths.isSafe(state.getPath())) {
                // 脏路径直接跳过，不因为一条历史脏数据阻断整单
                log.warn("跳过不合法的同步路径：{}", state.getPath());
                continue;
            }
            files.add(deleteFile(state));
        }

        if (files.isEmpty()) {
            throw BusinessException.badRequest("还没有可导入的日记、经验或知识");
        }

        ObsidianImportTask task = new ObsidianImportTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setInstallationId(plugin.getInstallationId());
        task.setStatus(PENDING);
        task.setTotal(files.size());
        task.setProcessed(0);
        task.setSuccessCount(0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        task.setConflictCount(0);
        task.setMessage("");
        task.setCreatedAt(LocalDateTime.now());
        task.setExpiredAt(LocalDateTime.now().plus(TASK_TTL));
        taskMapper.insert(task);

        for (ObsidianImportTaskFile file : files) {
            file.setTaskId(task.getTaskId());
            fileMapper.insert(file);
        }

        ObsidianImportTaskVO vo = toTaskVO(task, false);
        streamService.publishAfterCommit(task.getTaskId(), vo);
        return vo;
    }

    @Override
    public ObsidianImportTaskVO task(String taskId) {
        Long userId = CurrentUser.id();
        expireStale(userId);
        ObsidianImportTask task = taskMapper.selectOne(Wrappers.<ObsidianImportTask>lambdaQuery()
                .eq(ObsidianImportTask::getTaskId, taskId)
                .eq(ObsidianImportTask::getUserId, userId)
                .last("limit 1"));
        if (task == null) {
            throw BusinessException.notFound("任务不存在或已过期");
        }
        return toTaskVO(task, true);
    }

    // ==================== 领任务（插件） ====================

    @Override
    @Transactional
    public ObsidianPluginTaskVO pendingTask() {
        ObsidianPlugin plugin = PluginContext.get();
        expireStale(plugin.getUserId());

        ObsidianImportTask task = taskMapper.selectOne(Wrappers.<ObsidianImportTask>lambdaQuery()
                .eq(ObsidianImportTask::getUserId, plugin.getUserId())
                .eq(ObsidianImportTask::getInstallationId, plugin.getInstallationId())
                .in(ObsidianImportTask::getStatus, PENDING, RUNNING)
                .orderByDesc(ObsidianImportTask::getId)
                .last("limit 1"));
        if (task == null) {
            return null;
        }

        boolean started = PENDING.equals(task.getStatus());
        if (started) {
            task.setStatus(RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        List<ObsidianImportTaskFile> files = fileMapper.selectList(
                Wrappers.<ObsidianImportTaskFile>lambdaQuery()
                        .eq(ObsidianImportTaskFile::getTaskId, task.getTaskId())
                        .orderByAsc(ObsidianImportTaskFile::getId));

        List<ObsidianTaskFileVO> payload = new ArrayList<>(files.size());
        for (ObsidianImportTaskFile file : files) {
            ObsidianTaskFileVO item = new ObsidianTaskFileVO();
            item.setPath(file.getPath());
            item.setContent(file.getContent());
            item.setHash(file.getHash());
            item.setOpType(file.getOpType());
            item.setAssetType(file.getAssetType());
            item.setAssetId(file.getAssetId());
            item.setLastSyncedHash(file.getLastSyncedHash());
            payload.add(item);
        }

        ObsidianPluginTaskVO vo = new ObsidianPluginTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setTotal(files.size());
        vo.setFiles(payload);

        if (started) {
            // 让 Web 从"等待 Obsidian"切到"同步中"，否则用户要等到第一次 progress 才有反馈
            streamService.publishAfterCommit(task.getTaskId(), toTaskVO(task, false));
        }
        return vo;
    }

    @Override
    @Transactional
    public void progress(String taskId, ObsidianProgressRequest request) {
        ObsidianPlugin plugin = PluginContext.get();
        ObsidianImportTask task = requireTask(taskId, plugin);
        if (isFinished(task.getStatus())) {
            return;
        }

        task.setStatus(RUNNING);
        task.setProcessed(Math.max(task.getProcessed() == null ? 0 : task.getProcessed(), request.getProcessed()));
        task.setSuccessCount(request.getSuccess());
        task.setSkippedCount(request.getSkipped());
        task.setFailedCount(request.getFailed());
        task.setConflictCount(request.getConflict());
        if (StringUtils.hasText(request.getMessage())) {
            task.setMessage(truncate(request.getMessage()));
        }
        taskMapper.updateById(task);

        streamService.publishAfterCommit(task.getTaskId(), toTaskVO(task, false));
    }

    @Override
    @Transactional
    public void complete(String taskId, ObsidianCompleteRequest request) {
        ObsidianPlugin plugin = PluginContext.get();
        ObsidianImportTask task = requireTask(taskId, plugin);
        if (isFinished(task.getStatus())) {
            return;
        }

        int total = task.getTotal() == null ? 0 : task.getTotal();
        int failed = Math.max(0, request.getFailedCount());
        int conflict = Math.max(0, request.getConflictCount());

        // 以计数为准，而不是照抄插件传的 status：两者矛盾时计数才是事实
        String status = COMPLETED;
        if (failed > 0 || conflict > 0) {
            status = total > 0 && failed >= total ? FAILED : PARTIAL_FAILED;
        }

        task.setStatus(status);
        task.setProcessed(request.getSuccessCount() + request.getSkippedCount() + failed + conflict);
        task.setSuccessCount(request.getSuccessCount());
        task.setSkippedCount(request.getSkippedCount());
        task.setFailedCount(failed);
        task.setConflictCount(conflict);
        task.setMessage(truncate(request.getMessage()));
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            applyFileResults(task, request.getFiles());
        } else if (failed == 0 && conflict == 0) {
            // 没带逐篇结果时只在"全部成功"的前提下记快照；有任何失败就等下次重写，宁可多写不可漏记
            markAllSynced(task);
        }

        streamService.publishAfterCommit(task.getTaskId(), toTaskVO(task, true));
    }

    // ==================== 内部 ====================

    private ObsidianImportTaskFile buildFile(RenderedNote note, ObsidianSyncState state) {
        ObsidianImportTaskFile file = new ObsidianImportTaskFile();
        file.setPath(note.path());
        file.setContent(note.content());
        file.setHash(note.hash());
        file.setOpType(state == null ? CREATE : UPDATE);
        file.setAssetType(note.assetType());
        file.setAssetId(note.assetId());
        file.setLastSyncedHash(state == null ? "" : state.getLastSyncedHash());
        file.setStatus(FILE_PENDING);
        file.setError("");
        return file;
    }

    private ObsidianImportTaskFile deleteFile(ObsidianSyncState state) {
        ObsidianImportTaskFile file = new ObsidianImportTaskFile();
        file.setPath(state.getPath());
        file.setContent(null);
        file.setHash("");
        file.setOpType(DELETE);
        file.setAssetType(state.getAssetType());
        file.setAssetId(state.getAssetId());
        file.setLastSyncedHash(state.getLastSyncedHash());
        file.setStatus(FILE_PENDING);
        file.setError("");
        return file;
    }

    private void applyFileResults(ObsidianImportTask task, List<ObsidianFileResultVO> results) {
        Map<String, ObsidianImportTaskFile> byPath = new LinkedHashMap<>();
        for (ObsidianImportTaskFile file : fileMapper.selectList(
                Wrappers.<ObsidianImportTaskFile>lambdaQuery()
                        .eq(ObsidianImportTaskFile::getTaskId, task.getTaskId()))) {
            byPath.put(file.getPath(), file);
        }

        LocalDateTime now = LocalDateTime.now();
        for (ObsidianFileResultVO result : results) {
            ObsidianImportTaskFile file = byPath.get(result.getPath());
            if (file == null) {
                continue;
            }
            String status = normalizeFileStatus(result.getStatus());
            file.setStatus(status);
            file.setError(truncate(result.getError()));
            file.setProcessedAt(now);
            fileMapper.updateById(file);

            if (FILE_SUCCESS.equals(status) || FILE_SKIPPED.equals(status)) {
                if (DELETE.equals(file.getOpType())) {
                    removeSyncState(task.getInstallationId(), file.getPath());
                } else {
                    upsertSyncState(task, file);
                }
            }
            // FAILED / CONFLICT 不动快照：下次仍会重算，冲突不会因为一次失败的上报被吞掉
        }
    }

    private void markAllSynced(ObsidianImportTask task) {
        List<ObsidianImportTaskFile> files = fileMapper.selectList(
                Wrappers.<ObsidianImportTaskFile>lambdaQuery()
                        .eq(ObsidianImportTaskFile::getTaskId, task.getTaskId()));
        for (ObsidianImportTaskFile file : files) {
            if (DELETE.equals(file.getOpType())) {
                removeSyncState(task.getInstallationId(), file.getPath());
            } else {
                upsertSyncState(task, file);
            }
        }
    }

    private void upsertSyncState(ObsidianImportTask task, ObsidianImportTaskFile file) {
        String pathHash = Digests.sha256(file.getPath());
        ObsidianSyncState state = syncStateMapper.selectOne(Wrappers.<ObsidianSyncState>lambdaQuery()
                .eq(ObsidianSyncState::getInstallationId, task.getInstallationId())
                .eq(ObsidianSyncState::getPathHash, pathHash)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (state == null) {
            state = new ObsidianSyncState();
            state.setUserId(task.getUserId());
            state.setInstallationId(task.getInstallationId());
            state.setPath(file.getPath());
            state.setPathHash(pathHash);
            state.setAssetType(file.getAssetType());
            state.setAssetId(file.getAssetId());
            state.setLastSyncedHash(file.getHash());
            state.setLastSyncedAt(now);
            syncStateMapper.insert(state);
            return;
        }
        state.setPath(file.getPath());
        state.setAssetType(file.getAssetType());
        state.setAssetId(file.getAssetId());
        state.setLastSyncedHash(file.getHash());
        state.setLastSyncedAt(now);
        syncStateMapper.updateById(state);
    }

    private void removeSyncState(String installationId, String path) {
        syncStateMapper.delete(Wrappers.<ObsidianSyncState>lambdaQuery()
                .eq(ObsidianSyncState::getInstallationId, installationId)
                .eq(ObsidianSyncState::getPathHash, Digests.sha256(path)));
    }

    private Map<String, ObsidianSyncState> loadStates(String installationId) {
        Map<String, ObsidianSyncState> states = new LinkedHashMap<>();
        for (ObsidianSyncState state : syncStateMapper.selectList(Wrappers.<ObsidianSyncState>lambdaQuery()
                .eq(ObsidianSyncState::getInstallationId, installationId))) {
            states.put(state.getPath(), state);
        }
        return states;
    }

    /** 超过有效期的未完成任务惰性置 EXPIRED：不做定时任务，查询时顺手处理 */
    private void expireStale(Long userId) {
        taskMapper.update(null, Wrappers.<ObsidianImportTask>lambdaUpdate()
                .eq(ObsidianImportTask::getUserId, userId)
                .in(ObsidianImportTask::getStatus, PENDING, RUNNING)
                .lt(ObsidianImportTask::getExpiredAt, LocalDateTime.now())
                .set(ObsidianImportTask::getStatus, EXPIRED));
    }

    /** 同一插件实例的旧任务被新任务顶掉，避免插件并发跑两份互抢文件 */
    private void supersedeOpen(Long userId, String installationId) {
        taskMapper.update(null, Wrappers.<ObsidianImportTask>lambdaUpdate()
                .eq(ObsidianImportTask::getUserId, userId)
                .eq(ObsidianImportTask::getInstallationId, installationId)
                .in(ObsidianImportTask::getStatus, PENDING, RUNNING)
                .set(ObsidianImportTask::getStatus, SUPERSEDED));
    }

    private ObsidianImportTask requireTask(String taskId, ObsidianPlugin plugin) {
        ObsidianImportTask task = taskMapper.selectOne(Wrappers.<ObsidianImportTask>lambdaQuery()
                .eq(ObsidianImportTask::getTaskId, taskId)
                .eq(ObsidianImportTask::getUserId, plugin.getUserId())
                .eq(ObsidianImportTask::getInstallationId, plugin.getInstallationId())
                .last("limit 1"));
        if (task == null) {
            throw BusinessException.notFound("任务不存在，或不属于当前插件实例");
        }
        if (EXPIRED.equals(task.getStatus())) {
            throw BusinessException.badRequest("任务已过期，请在 MemoAgent 重新点击导入");
        }
        return task;
    }

    private ObsidianPlugin findPlugin(Long userId) {
        return pluginMapper.selectOne(Wrappers.<ObsidianPlugin>lambdaQuery()
                .eq(ObsidianPlugin::getUserId, userId)
                .eq(ObsidianPlugin::getRevoked, 0)
                .orderByDesc(ObsidianPlugin::getId)
                .last("limit 1"));
    }

    private ObsidianPluginVO toPluginVO(ObsidianPlugin plugin) {
        ObsidianPluginVO vo = new ObsidianPluginVO();
        if (plugin == null) {
            vo.setPaired(false);
            return vo;
        }
        vo.setPaired(true);
        vo.setInstallationId(plugin.getInstallationId());
        vo.setVaultName(plugin.getVaultName());
        vo.setPairedAt(plugin.getCreatedAt());
        vo.setLastActiveAt(plugin.getLastActiveAt());
        return vo;
    }

    private ObsidianImportTaskVO toTaskVO(ObsidianImportTask task, boolean withProblems) {
        ObsidianImportTaskVO vo = new ObsidianImportTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(task.getStatus());
        vo.setTotal(nvl(task.getTotal()));
        vo.setProcessed(nvl(task.getProcessed()));
        vo.setSuccessCount(nvl(task.getSuccessCount()));
        vo.setSkippedCount(nvl(task.getSkippedCount()));
        vo.setFailedCount(nvl(task.getFailedCount()));
        vo.setConflictCount(nvl(task.getConflictCount()));
        vo.setMessage(task.getMessage() == null ? "" : task.getMessage());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setExpiredAt(task.getExpiredAt());

        if (withProblems) {
            List<ObsidianImportTaskFile> problems = fileMapper.selectList(
                    Wrappers.<ObsidianImportTaskFile>lambdaQuery()
                            .eq(ObsidianImportTaskFile::getTaskId, task.getTaskId())
                            .in(ObsidianImportTaskFile::getStatus, FILE_FAILED, FILE_CONFLICT)
                            .orderByAsc(ObsidianImportTaskFile::getId)
                            .last("limit 50"));
            List<ObsidianFileResultVO> items = new ArrayList<>(problems.size());
            for (ObsidianImportTaskFile problem : problems) {
                ObsidianFileResultVO item = new ObsidianFileResultVO();
                item.setPath(problem.getPath());
                item.setStatus(problem.getStatus());
                item.setError(problem.getError());
                items.add(item);
            }
            vo.setProblems(items);
        }
        return vo;
    }

    private String normalizeFileStatus(String status) {
        if (FILE_SUCCESS.equals(status) || FILE_SKIPPED.equals(status)
                || FILE_FAILED.equals(status) || FILE_CONFLICT.equals(status)) {
            return status;
        }
        return FILE_FAILED;
    }

    private boolean isFinished(String status) {
        return COMPLETED.equals(status) || PARTIAL_FAILED.equals(status)
                || FAILED.equals(status) || EXPIRED.equals(status) || SUPERSEDED.equals(status);
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hint(String token) {
        if (token.length() <= 8) {
            return "";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }

    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String single = text.replace("\r", " ").replace("\n", " ").trim();
        return single.length() <= ERROR_MAX ? single : single.substring(0, ERROR_MAX);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
