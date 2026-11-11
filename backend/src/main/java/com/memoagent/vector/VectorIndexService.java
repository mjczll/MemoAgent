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
 *   仍是同步执行，异步下沉到阶段 7（见开发步骤第 10 节）。
 * - 事件只带 sourceType / sourceId，这里回查 MySQL 取最终正文，向量表不复制业务正文，
 *   只存用于排查的被 embed 文本 + 回指信息。
 * - 向量是旁路索引：模型不可用、维度不符、pgvector 连不上都不阻断主保存流程，
 *   记 warn 后跳过；后续补量重新 embed 即可恢复。
 * - 用 ObjectProvider 懒取仓储，memoagent.vector.enabled=false 时容器里没有这个 Bean，
 *   应用照样能启动和运行，只是不写向量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexService {

    /** 单次 embed 的文本上限。超长正文只保留标题与开头，避免打爆模型输入长度。 */
    private static final int MAX_CONTENT_CHARS = 6000;

    private final ObjectProvider<PgVectorRepository> repositoryProvider;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    /**
     * fallbackExecution=true：万一以后某个保存入口漏了 @Transactional，事件仍然会被处理，
     * 不至于静默丢掉整条向量。所有现有保存点都在事务内，正常路径走的是 AFTER_COMMIT。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssetChanged(AssetVectorEvent event) {
        if (event.operation() == AssetVectorEvent.Operation.DELETE) {
            remove(event.sourceType(), event.sourceId());
        } else {
            index(event.sourceType(), event.sourceId());
        }
    }

    /**
     * 写入单条向量。事件监听和存量补量都走这里。
     * 返回是否成功：向量库未启用或单条失败都返回 false，绝不把异常抛给保存流程。
     */
    public boolean index(String sourceType, long sourceId) {
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("向量库未启用，跳过 {} #{}", sourceType, sourceId);
            return false;
        }
        try {
            AssetBody body = load(sourceType, sourceId);
            if (body == null) {
                // 资产已不存在（例如提交后立刻被删），清掉可能残留的旧向量
                repository.delete(sourceType, sourceId);
                return true;
            }
            repository.upsert(sourceType, sourceId, body.userId(), body.content());
            log.info("向量已写入：{} #{}（{} 字）", sourceType, sourceId, body.content().length());
            return true;
        } catch (RuntimeException exception) {
            log.warn("向量写入失败，已跳过（MySQL 里的资产不受影响）：{} #{}",
                    sourceType, sourceId, exception);
            return false;
        }
    }

    /** 清理单条向量。删除事件和补量时的残留清理都走这里。 */
    public boolean remove(String sourceType, long sourceId) {
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return false;
        }
        try {
            repository.delete(sourceType, sourceId);
            log.info("向量已清理：{} #{}", sourceType, sourceId);
            return true;
        } catch (RuntimeException exception) {
            log.warn("向量清理失败，已跳过：{} #{}", sourceType, sourceId, exception);
            return false;
        }
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
}
