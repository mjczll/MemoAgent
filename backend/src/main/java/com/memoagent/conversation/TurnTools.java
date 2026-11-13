package com.memoagent.conversation;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一轮对话里的可选工具。普通追问不调用。
 */
public class TurnTools {

    /**
     * 兜底引用最多挂几条。挂太多会让模型倾向于「报清单」而不是讲内容，
     * 也把页面塞得很满；真正相关的通常不会超过三条。
     */
    private static final int MAX_FALLBACK_REFS = 3;

    private final Set<Long> diaryIds;
    private final Set<Long> experienceIds;
    private final Set<Long> knowledgeIds;
    private final Map<String, String> titles = new LinkedHashMap<>();
    /** 引用抽屉要展示摘要，构造时和 title 一起从命中里带出来 */
    private final Map<String, String> excerpts = new LinkedHashMap<>();
    /** 一句话概括，经验取结论 */
    private final Map<String, String> summaries = new LinkedHashMap<>();

    private final List<RecallHit> hits;

    private boolean organize;
    private MessagePayload.SupplementPayload supplement;
    private final List<MessagePayload.LinkPayload> links = new ArrayList<>();
    private final List<MessagePayload.RefPayload> refs = new ArrayList<>();

    /** 本轮是不是复盘：模型调用过 listAssets / assetStats 时置位，落库时据此标记日记来源 */
    private boolean review;
    private String reviewFrom;
    private String reviewTo;

    public TurnTools(List<RecallHit> hits) {
        Set<Long> diaries = new LinkedHashSet<>();
        Set<Long> experiences = new LinkedHashSet<>();
        Set<Long> knowledge = new LinkedHashSet<>();
        if (hits != null) {
            for (RecallHit hit : hits) {
                if (hit.id() == null) {
                    continue;
                }
                titles.put(key(hit.type(), hit.id()), hit.title());
                excerpts.put(key(hit.type(), hit.id()), hit.excerpt());
                summaries.put(key(hit.type(), hit.id()), hit.summary());
                if ("diary".equals(hit.type())) {
                    diaries.add(hit.id());
                } else if ("experience".equals(hit.type())) {
                    experiences.add(hit.id());
                } else if ("knowledge".equals(hit.type())) {
                    knowledge.add(hit.id());
                }
            }
        }
        this.diaryIds = diaries;
        this.experienceIds = experiences;
        this.knowledgeIds = knowledge;
        this.hits = hits == null ? List.of() : List.copyOf(hits);
    }

    @Tool(name = "offerOrganize", description = "用户已经讲了自己怎么做的或收获，或明确说帮我整理、就这些时必须调用。只描述现象、开场、继续追问、解释概念、回答旧记录时不要调用。调用后正文不要再问整理成日记还是经验，也不要让用户说整理。")
    public String offerOrganize() {
        organize = true;
        return "已记下，可以请用户整理。";
    }

    @Tool(name = "supplementKnowledge", description = "用户没听懂、需要用你的知识写一段说明时调用。不要把说明说成用户自己的经历。普通追问不要调用。")
    public String supplementKnowledge(
            @ToolParam(description = "知识标题") String title,
            @ToolParam(description = "领域，例如 Redis") String domain,
            @ToolParam(description = "分类") String category,
            @ToolParam(description = "一句话摘要") String summary,
            @ToolParam(description = "说明正文") String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return "标题和正文不能为空，这次补充没有记下。";
        }
        MessagePayload.SupplementPayload payload = new MessagePayload.SupplementPayload();
        payload.setTitle(title.trim());
        payload.setDomain(domain == null ? "" : domain.trim());
        payload.setCategory(category == null || category.isBlank() ? "未分类" : category.trim());
        payload.setSummary(summary == null ? "" : summary.trim());
        payload.setContent(content.trim());
        supplement = payload;
        return "已记下补充，要等用户同意才会写入知识库。";
    }

    @Tool(name = "linkKnowledge", description = "候选知识与用户正在讲的事是同一主题时必须调用，包括用户只描述了现象的那一轮。knowledgeIds 只能来自候选记录。日记和经验不要放进这个工具。")
    public String linkKnowledge(@ToolParam(description = "候选知识 id") List<Long> knowledgeIds) {
        int added = addLinks(knowledgeIds);
        if (added == 0) {
            return "没有可用的候选知识。";
        }
        return "已记下 " + added + " 条可关联的知识。";
    }

    @Tool(name = "citeRecords", description = "回答用户关于过去记录的问题时，列出实际用到的候选 id。没有用到就不要调用。")
    public String citeRecords(
            @ToolParam(description = "用到的日记 id", required = false) List<Long> diaryIds,
            @ToolParam(description = "用到的经验 id", required = false) List<Long> experienceIds,
            @ToolParam(description = "用到的知识 id", required = false) List<Long> knowledgeIds) {
        int added = addRefs("diary", diaryIds, this.diaryIds);
        added += addRefs("experience", experienceIds, this.experienceIds);
        added += addRefs("knowledge", knowledgeIds, this.knowledgeIds);
        if (added == 0) {
            return "没有可用的候选记录。";
        }
        return "已记下引用。";
    }

    public boolean offeredOrganize() {
        return organize;
    }

    public void proposeRelated(List<String> keywords, Set<Long> exceptKnowledgeIds) {
        List<String> strong = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && keyword.length() >= 4 && !strong.contains(keyword)) {
                    strong.add(keyword);
                }
            }
        }
        if (strong.isEmpty()) {
            return;
        }
        Set<Long> skipped = exceptKnowledgeIds == null ? Set.of() : exceptKnowledgeIds;
        if (links.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (RecallHit hit : hits) {
                if (!"knowledge".equals(hit.type()) || hit.id() == null || skipped.contains(hit.id())) {
                    continue;
                }
                if (containsStrong(hit, strong)) {
                    ids.add(hit.id());
                }
            }
            addLinks(ids);
        }
        if (refs.isEmpty()) {
            for (RecallHit hit : hits) {
                if (refs.size() >= MAX_FALLBACK_REFS) {
                    break;
                }
                if (hit.id() == null || "knowledge".equals(hit.type()) || !containsStrong(hit, strong)) {
                    continue;
                }
                if ("diary".equals(hit.type())) {
                    addRefs("diary", List.of(hit.id()), diaryIds);
                } else if ("experience".equals(hit.type())) {
                    addRefs("experience", List.of(hit.id()), experienceIds);
                }
            }
        }
    }

    /**
     * 把「按时间列出的记录」全部登记成引用，不设上限。
     * <p>
     * 与 citeRecords 的区别：那条路只收本轮语义召回的候选 id，
     * 而复盘查的是整段时间的记录，本来就不在候选里，所以这里不做 id 白名单校验。
     * 条数由 listAssets 的 limit 决定（默认 30、最多 60），
     * 也就是「列出多少就挂多少」，不做二次截断，避免正文讲到的记录在抽屉里找不到。
     */
    public void citeListing(List<AssetQueryTools.Row> rows) {
        if (rows == null) {
            return;
        }
        // 只要调用了按时间取数的工具，就认定这轮是复盘（无论结果是否为空）
        review = true;
        for (AssetQueryTools.Row row : rows) {
            String type = row.kind();
            if (!"diary".equals(type) && !"experience".equals(type) && !"knowledge".equals(type)) {
                continue;
            }
            boolean exists = refs.stream()
                    .anyMatch(item -> type.equals(item.getType()) && row.id().equals(item.getId()));
            if (exists) {
                continue;
            }
            MessagePayload.RefPayload ref = new MessagePayload.RefPayload();
            ref.setType(type);
            ref.setId(row.id());
            ref.setTitle(row.title());
            ref.setExcerpt(row.excerpt());
            refs.add(ref);
        }
    }

    private static boolean containsStrong(RecallHit hit, List<String> strong) {
        String haystack = (hit.title() == null ? "" : hit.title()) + (hit.excerpt() == null ? "" : hit.excerpt());
        for (String keyword : strong) {
            if (haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String kind() {
        if (supplement != null) {
            return TurnSanitizer.SUPPLEMENT;
        }
        if (!links.isEmpty()) {
            return TurnSanitizer.ASSOCIATION;
        }
        if (review) {
            return TurnSanitizer.REVIEW;
        }
        if (!refs.isEmpty()) {
            return TurnSanitizer.RECALL;
        }
        return TurnSanitizer.INTERVIEW;
    }

    public boolean isReview() {
        return review;
    }

    public void apply(MessagePayload payload) {
        payload.setSupplement(supplement);
        payload.setLinks(List.copyOf(links));
        payload.setRefs(List.copyOf(refs));
    }

    int addLinks(List<Long> requested) {
        if (requested == null) {
            return 0;
        }
        int added = 0;
        for (Long id : requested) {
            if (id == null || !knowledgeIds.contains(id) || links.stream().anyMatch(item -> id.equals(item.getKnowledgeId()))) {
                continue;
            }
            MessagePayload.LinkPayload link = new MessagePayload.LinkPayload();
            link.setKnowledgeId(id);
            link.setTitle(titles.getOrDefault(key("knowledge", id), ""));
            links.add(link);
            added += 1;
        }
        return added;
    }

    private int addRefs(String type, List<Long> requested, Set<Long> allowed) {
        if (requested == null) {
            return 0;
        }
        int added = 0;
        for (Long id : requested) {
            if (id == null || !allowed.contains(id) || refs.stream().anyMatch(item -> type.equals(item.getType()) && id.equals(item.getId()))) {
                continue;
            }
            MessagePayload.RefPayload ref = new MessagePayload.RefPayload();
            ref.setType(type);
            ref.setId(id);
            ref.setTitle(titles.getOrDefault(key(type, id), ""));
            ref.setExcerpt(excerpts.get(key(type, id)));
            ref.setSummary(summaries.get(key(type, id)));
            refs.add(ref);
            added += 1;
        }
        return added;
    }

    private String key(String type, Long id) {
        return type + ":" + id;
    }
}
