package com.memoagent.conversation;

import com.memoagent.service.SemanticSearchService;
import com.memoagent.vector.VectorSourceTypes;
import com.memoagent.vo.SearchHitVO;
import com.memoagent.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 模型可自由调用的只读数据工具。
 * <p>
 * 只给「查」，不给「写」：写操作仍然只能通过 offerOrganize 走用户确认。
 * 所有查询都走 {@link SemanticSearchService}，用户维度由它按 {@code CurrentUser} 过滤，
 * 模型拿不到别人的数据。
 * <p>
 * 刻意做成无状态 Bean，而不是塞进每轮 new 出来的 {@link TurnTools}：
 * 查询不产生任何 UI 状态（refs / links），和「本轮命中候选」是两件事。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetQueryTools {

    /** 默认给几条。给太多会挤占上下文，也让模型挑花眼。 */
    private static final int DEFAULT_LIMIT = 8;

    /** 上限。模型可以要更多，但不允许无限拉。 */
    private static final int MAX_LIMIT = 20;

    private final SemanticSearchService semanticSearchService;

    @Tool(
            name = "searchAssets",
            description = """
                    按语义检索用户自己的日记、经验和知识。用户想找过去的记录、需要相关素材、\
                    或要你回顾某段时间时调用。query 用一段话描述要找的东西，不要只截关键词。\
                    只返回标题和摘要，不要据此编造正文细节。""")
    public String searchAssets(
            @ToolParam(description = "要找的内容，一句话描述即可") String query,
            @ToolParam(description = "限定类型：diary、experience、knowledge，多个用逗号分隔；不填表示全部", required = false) String kinds,
            @ToolParam(description = "返回条数，默认 8，最多 20", required = false) Integer limit) {
        if (!StringUtils.hasText(query)) {
            return "没有提供检索内容。";
        }
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        List<String> types = parseKinds(kinds);
        SearchResultVO result = semanticSearchService.search(query.trim(), types, size, null);
        if (!result.isAvailable()) {
            return "检索暂不可用（" + (StringUtils.hasText(result.getNotice()) ? result.getNotice() : "向量库未启用")
                    + "），可以先让用户手动查找，或稍后再试。";
        }
        List<SearchHitVO> hits = result.getHits();
        if (hits.isEmpty()) {
            return "没有找到相关记录。";
        }
        StringBuilder text = new StringBuilder("找到 ").append(hits.size()).append(" 条相关记录：\n");
        int order = 1;
        for (SearchHitVO hit : hits) {
            text.append(order).append(". [").append(label(hit.getKind()))
                    .append(" #").append(hit.getId()).append("] ")
                    .append(hit.getTitle());
            if (StringUtils.hasText(hit.getExcerpt())) {
                text.append("｜").append(hit.getExcerpt());
            }
            text.append("（相似度 ")
                    .append(String.format(Locale.ROOT, "%.2f", hit.getScore()))
                    .append("）\n");
            order += 1;
        }
        log.info("模型检索「{}」命中 {} 条", query, hits.size());
        return text.toString();
    }

    private List<String> parseKinds(String kinds) {
        if (!StringUtils.hasText(kinds)) {
            return null;
        }
        return Arrays.stream(kinds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String label(String kind) {
        if (VectorSourceTypes.DIARY.equals(kind)) {
            return "日记";
        }
        if (VectorSourceTypes.EXPERIENCE.equals(kind)) {
            return "经验";
        }
        if (VectorSourceTypes.KNOWLEDGE.equals(kind)) {
            return "知识";
        }
        return kind;
    }
}
