package com.memoagent.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.Texts;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型可自由调用的只读数据工具。
 * <p>
 * 只给「查」，不给「写」：写操作仍然只能通过 offerOrganize 走用户确认。
 * 所有查询都按 {@code CurrentUser} 过滤，模型拿不到别人的数据。
 * <p>
 * 两种查询维度：
 * <ul>
 *   <li>{@code searchAssets}：按语义找几条，适合「我以前是不是写过……」；</li>
 *   <li>{@code listAssets} / {@code assetStats}：按日期取全量并计数，适合「这个月我都做了什么」。</li>
 * </ul>
 * 后者是复盘的基础——语义检索既没有时间维度，也不保证覆盖全部。
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

    /** 按时间列清单的默认与上限，复盘要看得全，比语义检索给得多。 */
    private static final int DEFAULT_LIST_LIMIT = 30;

    private static final int MAX_LIST_LIMIT = 60;

    /** 没给日期时的默认窗口：最近 30 天。 */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** 摘要给多长。复盘要看到「做了什么」，比搜索结果长一点。 */
    private static final int EXCERPT_MAX = 120;

    /** 报表里最多列几个领域，其余合并。 */
    private static final int TOP_DOMAINS = 8;

    private final SemanticSearchService semanticSearchService;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

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

    @Tool(
            name = "listAssets",
            description = """
                    按日期范围列出用户这段时间写过的日记、经验和知识，按时间倒序，不走向量检索、\
                    结果覆盖该范围内的全部记录。用户问「这个月我都做了什么」「最近两周干了什么」\
                    「上个月有哪些记录」这类按时间回顾的问题时调用，不要用 searchAssets 代替。\
                    日期格式必须是 yyyy-MM-dd，包含首尾两天。""")
    public String listAssets(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，含当天", required = false) String from,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，含当天；不填表示今天", required = false) String to,
            @ToolParam(description = "限定类型：diary、experience、knowledge，多个用逗号分隔；不填表示全部", required = false) String kinds,
            @ToolParam(description = "返回条数，默认 30，最多 60", required = false) Integer limit) {
        return listing(from, to, kinds, limit).text();
    }

    /** 清单结果。文本给模型看，记录本身给 {@link TurnAssetTools} 挂引用用。 */
    record Listing(String text, List<Row> rows) {
    }

    /**
     * 清单的实际实现：同时产出给模型的文本和给页面的记录。
     * <p>
     * 单独拆出来是因为「同一批记录」有两个消费方：模型读文本写正文，
     * 页面需要一份 refs 才能显示「搜索到 N 条相关引用」的胶囊。
     */
    Listing listing(String from, String to, String kinds, Integer limit) {
        LocalDate end;
        LocalDate start;
        try {
            end = parseDate(to, LocalDate.now());
            start = parseDate(from, end.minusDays(DEFAULT_WINDOW_DAYS - 1L));
        } catch (DateTimeParseException exception) {
            return new Listing("日期格式不对，请用 yyyy-MM-dd，例如 2026-11-01。", List.of());
        }
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        int size = limit == null ? DEFAULT_LIST_LIMIT : Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
        List<Row> rows = query(start, end, parseKinds(kinds));
        if (rows.isEmpty()) {
            return new Listing(range(start, end) + " 这段时间没有记录。", List.of());
        }
        StringBuilder text = new StringBuilder();
        text.append(range(start, end)).append(" 共 ").append(rows.size()).append(" 条");
        if (rows.size() > size) {
            text.append("（先列最近 ").append(size).append(" 条）");
        }
        text.append("：\n");
        int order = 1;
        for (Row row : rows) {
            if (order > size) {
                break;
            }
            text.append(order).append(". [").append(label(row.kind()))
                    .append(" ").append(row.date()).append(" #").append(row.id()).append("] ")
                    .append(row.title());
            if (StringUtils.hasText(row.excerpt())) {
                text.append("｜").append(row.excerpt());
            }
            text.append("\n");
            order += 1;
        }
        if (rows.size() > size) {
            text.append("（还有 ").append(rows.size() - size).append(" 条没列出）\n");
        }
        log.info("模型按时间列清单 {} ~ {}，命中 {} 条", start, end, rows.size());
        return new Listing(text.toString(), rows);
    }

    @Tool(
            name = "assetStats",
            description = """
                    统计用户一段时间内日记、经验、知识的条数与分布，不返回正文。\
                    用户要复盘、要「这个月做了哪些」「最近的情况」时，先调用它拿到准确数字，\
                    再调用 listAssets 取清单；正文里的条数必须照抄这里的统计，不要自己数。\
                    日期格式必须是 yyyy-MM-dd，包含首尾两天。""")
    public String assetStats(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，含当天", required = false) String from,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，含当天；不填表示今天", required = false) String to) {
        LocalDate end;
        LocalDate start;
        try {
            end = parseDate(to, LocalDate.now());
            start = parseDate(from, end.minusDays(DEFAULT_WINDOW_DAYS - 1L));
        } catch (DateTimeParseException exception) {
            return "日期格式不对，请用 yyyy-MM-dd，例如 2026-11-01。";
        }
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        List<Row> rows = query(start, end, null);
        long diary = rows.stream().filter(row -> VectorSourceTypes.DIARY.equals(row.kind())).count();
        long experience = rows.stream().filter(row -> VectorSourceTypes.EXPERIENCE.equals(row.kind())).count();
        long knowledge = rows.stream().filter(row -> VectorSourceTypes.KNOWLEDGE.equals(row.kind())).count();
        StringBuilder text = new StringBuilder();
        text.append(range(start, end)).append(" 共 ").append(rows.size()).append(" 条：")
                .append("日记 ").append(diary).append(" 篇、")
                .append("经验 ").append(experience).append(" 条、")
                .append("知识 ").append(knowledge).append(" 条。\n");
        if (rows.isEmpty()) {
            return text.append("这段时间没有记录。").toString();
        }
        text.append("按天分布：");
        Map<String, Integer> byDay = new LinkedHashMap<>();
        for (Row row : rows) {
            byDay.merge(row.date().toString(), 1, Integer::sum);
        }
        byDay.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByKey())
                .forEach(entry -> text.append(entry.getKey()).append(" ").append(entry.getValue()).append(" 条，"));
        text.setLength(text.length() - 1);
        text.append("\n");
        Map<String, Integer> byDomain = new LinkedHashMap<>();
        for (Row row : rows) {
            String domain = StringUtils.hasText(row.domain()) ? row.domain() : "未标注领域";
            byDomain.merge(domain, 1, Integer::sum);
        }
        text.append("领域分布：");
        byDomain.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_DOMAINS)
                .forEach(entry -> text.append(entry.getKey()).append(" ").append(entry.getValue()).append(" 条，"));
        text.setLength(text.length() - 1);
        text.append("\n");
        log.info("模型取统计 {} ~ {}：{} 条", start, end, rows.size());
        return text.toString();
    }

    /** 一条记录在清单/统计里需要的最小信息。 */
    record Row(String kind, Long id, LocalDate date, String title, String excerpt, String domain) {
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return LocalDate.parse(value.trim());
    }

    private String range(LocalDate start, LocalDate end) {
        return start + " ~ " + end;
    }

    /**
     * 按日期范围取三类记录。日记用 diary_date（用户认定的日期），
     * 经验和知识没有业务日期，用创建时间的日期部分。
     */
    private List<Row> query(LocalDate start, LocalDate end, List<String> kinds) {
        boolean all = kinds == null || kinds.isEmpty();
        List<Row> rows = new ArrayList<>();
        if (all || kinds.contains(VectorSourceTypes.DIARY)) {
            List<Diary> diaries = diaryMapper.selectList(Wrappers.<Diary>lambdaQuery()
                    .eq(Diary::getUserId, CurrentUser.id())
                    .ge(Diary::getDiaryDate, start)
                    .le(Diary::getDiaryDate, end)
                    .orderByDesc(Diary::getDiaryDate)
                    .orderByDesc(Diary::getId));
            for (Diary diary : diaries) {
                // diary_date 是用户认定的日期，缺失时退回创建时间，保证清单里每条都有日期
                LocalDate date = diary.getDiaryDate();
                if (date == null && diary.getCreatedAt() != null) {
                    date = diary.getCreatedAt().toLocalDate();
                }
                if (date == null) {
                    continue;
                }
                rows.add(new Row(VectorSourceTypes.DIARY, diary.getId(), date,
                        diary.getTitle(), Texts.truncate(diary.getSummary(), EXCERPT_MAX), null));
            }
        }
        if (all || kinds.contains(VectorSourceTypes.EXPERIENCE)) {
            List<Experience> experiences = experienceMapper.selectList(Wrappers.<Experience>lambdaQuery()
                    .eq(Experience::getUserId, CurrentUser.id())
                    .ge(Experience::getCreatedAt, start.atStartOfDay())
                    .lt(Experience::getCreatedAt, end.plusDays(1).atStartOfDay())
                    .orderByDesc(Experience::getCreatedAt));
            for (Experience experience : experiences) {
                rows.add(new Row(VectorSourceTypes.EXPERIENCE, experience.getId(),
                        experience.getCreatedAt().toLocalDate(),
                        experience.getTitle(), Texts.truncate(experience.getLesson(), EXCERPT_MAX),
                        experience.getDomain()));
            }
        }
        if (all || kinds.contains(VectorSourceTypes.KNOWLEDGE)) {
            List<Knowledge> items = knowledgeMapper.selectList(Wrappers.<Knowledge>lambdaQuery()
                    .eq(Knowledge::getUserId, CurrentUser.id())
                    .ge(Knowledge::getCreatedAt, start.atStartOfDay())
                    .lt(Knowledge::getCreatedAt, end.plusDays(1).atStartOfDay())
                    .orderByDesc(Knowledge::getCreatedAt));
            for (Knowledge item : items) {
                rows.add(new Row(VectorSourceTypes.KNOWLEDGE, item.getId(),
                        item.getCreatedAt().toLocalDate(),
                        item.getTitle(), Texts.truncate(item.getSummary(), EXCERPT_MAX),
                        item.getDomain()));
            }
        }
        rows.sort(Comparator.comparing(Row::date).reversed());
        return rows;
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
