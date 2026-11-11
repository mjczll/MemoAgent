package com.memoagent.vector;

import com.memoagent.common.CurrentUser;
import com.memoagent.common.Texts;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 6 应用侧入口：把已落库的日记 / 经验 / 知识写进 pgvector。
 * <p>
 * 设计要点：
 * - 监听 AFTER_COMMIT，保证「保存成功后」才 embed；事务回滚时不会产生悬空向量。
 * - 方法上再加 @Async，切到独立线程执行：保存接口不再等 embedding 返回，
 *   采访整理一次写十几条也不会把接口拖成串行。失败的任务由
 *   {@link VectorCompensationService} 定时重试，不依赖用户手动补量。
 * - 事件只带 sourceType / sourceId，这里回查 MySQL 取最终正文，向量表不复制业务正文，
 *   只存用于排查的被 embed 文本 + 回指信息。
 * - 向量是旁路索引：模型不可用、维度不符、pgvector 连不上都不阻断主保存流程，
 *   记 warn 后跳过。跳过的那条会登记进 vector_task，由 {@link VectorCompensationService}
 *   定时重试，不需要人工再跑补量。
 * - 用 ObjectProvider 懒取仓储，memoagent.vector.enabled=false 时容器里没有这个 Bean，
 *   应用照样能启动和运行，只是不写向量（此时也不登记补偿任务，否则会攒一堆永远失败的待办）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexService {

    /** 单次 embed 的文本上限。超长正文只保留标题与开头，避免打爆模型输入长度。 */
    private static final int MAX_CONTENT_CHARS = 6000;

    private final ObjectProvider<PgVectorRepository> repositoryProvider;
    private final VectorTaskQueue taskQueue;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    /**
     * fallbackExecution=true：万一以后某个保存入口漏了 @Transactional，事件仍然会被处理，
     * 不至于静默丢掉整条向量。所有现有保存点都在事务内，正常路径走的是 AFTER_COMMIT。
     * <p>
     * @Async 让这段逻辑离开请求线程。事件本身在提交后才触发，所以异步执行时读到的一定是最终数据。
     */
    @Async("vectorExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssetChanged(AssetVectorEvent event) {
        if (event.operation() == AssetVectorEvent.Operation.DELETE) {
            remove(event.sourceType(), event.sourceId());
        } else {
            index(event.sourceType(), event.sourceId());
        }
    }

    /**
     * 写入单条向量，失败时自动登记补偿任务。事件监听和存量补量都走这里。
     * 返回是否成功：向量库未启用或单条失败都返回 false，绝不把异常抛给保存流程。
     */
    public boolean index(String sourceType, long sourceId) {
        IndexResult result = attempt(sourceType, sourceId);
        if (result == null) {
            return false;
        }
        if (result.success()) {
            // 之前可能失败过并留了任务，这次写成功了就把待办清掉
            clearQuietly(sourceType, sourceId);
            return true;
        }
        scheduleQuietly(sourceType, sourceId, result.error());
        return false;
    }

    /** 清理单条向量。删除事件和补量时的残留清理都走这里，失败同样登记补偿。 */
    public boolean remove(String sourceType, long sourceId) {
        IndexResult result = attemptRemove(sourceType, sourceId);
        if (result == null) {
            return false;
        }
        if (result.success()) {
            clearQuietly(sourceType, sourceId);
            return true;
        }
        scheduleQuietly(sourceType, sourceId, result.error());
        return false;
    }

    /**
     * 队列读写必须吞掉异常。这个类跑在 AFTER_COMMIT 回调里，
     * 一旦 vector_task 表还没建，抛出去会把「已经提交成功的保存请求」变成一个 500。
     * 补偿只是旁路，不允许反过来影响主流程。
     */
    private void scheduleQuietly(String sourceType, long sourceId, String error) {
        try {
            taskQueue.schedule(sourceType, sourceId, error);
        } catch (RuntimeException exception) {
            log.warn("登记向量补偿任务失败（vector_task 表是否已创建？）：{} #{}，{}",
                    sourceType, sourceId, exception.getMessage());
        }
    }

    private void clearQuietly(String sourceType, long sourceId) {
        try {
            taskQueue.clear(sourceType, sourceId);
        } catch (RuntimeException exception) {
            log.debug("清理向量补偿任务失败（vector_task 表是否已创建？）：{} #{}", sourceType, sourceId);
        }
    }

    /**
     * 只尝试一次，不登记补偿任务，供重试 worker 使用——否则重试失败会再入队一次，
     * 把退避次数打回原点，形成无限立即重试。
     * <p>
     * 返回 null 表示向量库未启用：此时既不成功也不该入队。
     */
    IndexResult attempt(String sourceType, long sourceId) {
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("向量库未启用，跳过 {} #{}", sourceType, sourceId);
            return null;
        }
        try {
            AssetBody body = load(sourceType, sourceId);
            if (body == null) {
                // 资产已不存在（例如提交后立刻被删），清掉可能残留的旧向量
                repository.delete(sourceType, sourceId);
                return IndexResult.ok();
            }
            repository.upsert(sourceType, sourceId, body.userId(), body.content());
            log.info("向量已写入：{} #{}（{} 字）", sourceType, sourceId, body.content().length());
            return IndexResult.ok();
        } catch (RuntimeException exception) {
            log.warn("向量写入失败，已登记补偿（MySQL 里的资产不受影响）：{} #{}",
                    sourceType, sourceId, exception);
            return IndexResult.failed(describe(exception));
        }
    }

    private IndexResult attemptRemove(String sourceType, long sourceId) {
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return null;
        }
        try {
            repository.delete(sourceType, sourceId);
            log.info("向量已清理：{} #{}", sourceType, sourceId);
            return IndexResult.ok();
        } catch (RuntimeException exception) {
            log.warn("向量清理失败，已登记补偿：{} #{}", sourceType, sourceId, exception);
            return IndexResult.failed(describe(exception));
        }
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        String type = exception.getClass().getSimpleName();
        return StringUtils.hasText(message) ? type + ": " + message : type;
    }

    private AssetBody load(String sourceType, long sourceId) {
        // 不用 switch(String)：sourceType 意外为 null 时会直接 NPE，这里要给出可排查的错误
        if (VectorSourceTypes.DIARY.equals(sourceType)) {
            return diaryBody(sourceId);
        }
        if (VectorSourceTypes.EXPERIENCE.equals(sourceType)) {
            return experienceBody(sourceId);
        }
        if (VectorSourceTypes.KNOWLEDGE.equals(sourceType)) {
            return knowledgeBody(sourceId);
        }
        throw new IllegalArgumentException("未知的向量来源类型：" + sourceType);
    }

    private AssetBody diaryBody(long id) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        add(parts, diary.getTitle());
        add(parts, diary.getSummary());
        add(parts, diary.getContent());
        return body(diary.getUserId(), parts);
    }

    private AssetBody experienceBody(long id) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        add(parts, experience.getTitle());
        addLabeled(parts, "问题：", experience.getProblem());
        addLabeled(parts, "原因：", experience.getCause());
        addLabeled(parts, "解决方案：", experience.getSolution());
        addLabeled(parts, "经验：", experience.getLesson());
        addLabeled(parts, "领域：", experience.getDomain());
        return body(experience.getUserId(), parts);
    }

    private AssetBody knowledgeBody(long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        add(parts, knowledge.getTitle());
        addLabeled(parts, "分类：", knowledge.getCategory());
        addLabeled(parts, "领域：", knowledge.getDomain());
        add(parts, knowledge.getSummary());
        add(parts, knowledge.getContent());
        return body(knowledge.getUserId(), parts);
    }

    private AssetBody body(Long userId, List<String> parts) {
        String content = Texts.truncate(String.join("\n", parts), MAX_CONTENT_CHARS);
        return new AssetBody(userId == null ? CurrentUser.id() : userId, content);
    }

    private static void add(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private static void addLabeled(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + value.trim());
        }
    }

    /** 正文为空时 PgVectorRepository.upsert 会自行删掉旧向量，不需要这里特判。 */
    private record AssetBody(long userId, String content) {
    }

    /** 单次尝试的结果。null 的 IndexResult 用来表示「向量库未启用」，与失败区分开。 */
    record IndexResult(boolean success, String error) {

        static IndexResult ok() {
            return new IndexResult(true, null);
        }

        static IndexResult failed(String error) {
            return new IndexResult(false, error);
        }
    }
}
