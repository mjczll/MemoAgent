package com.memoagent.obsidian;

import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产 → Obsidian Markdown 的纯函数渲染器：不查库、不写盘，只负责文本成型。
 *
 * 三条硬约束（决定导出的笔记在 Obsidian 里能不能正确工作）：
 * 1. 双链文本必须等于目标笔记的**文件名**（不含 .md），否则 Obsidian 解析不出链接；
 * 2. frontmatter 的字符串值统一双引号包裹并转义，避免标题里的冒号破坏 YAML；
 * 3. 文件名剔除 Windows/Obsidian 非法字符，且长度受限，重名由调用方追加 id 去重。
 */
public final class MarkdownRenderer {

    /** Obsidian 里按日期检索日记用，格式与 frontmatter 的 date 一致 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Windows 文件名保留字符 + Obsidian 双链里会产生歧义的字符 */
    private static final String ILLEGAL = "[\\\\/:*?\"<>|#^\\[\\]]";
    private static final int FILE_NAME_MAX = 60;

    private MarkdownRenderer() {
    }

    /** 日记：正文 + 关联的经验 / 知识双链。fileName 用于自引用锚点，便于从 Vault 反向跳回。 */
    public static String diary(Diary diary, List<String> tags,
                               List<String> experienceLinks, List<String> knowledgeLinks) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("type: diary");
        lines.add("date: " + (diary.getDiaryDate() == null ? "" : DATE.format(diary.getDiaryDate())));
        lines.add("origin: " + quote(origin(diary.getOrigin())));
        lines.add("memoagent-id: " + diary.getId());
        lines.add("created: " + quote(format(diary.getCreatedAt())));
        lines.add("updated: " + quote(format(diary.getUpdatedAt())));
        lines.addAll(tagLines(tags));
        lines.add("---");
        lines.add("");
        lines.add("# " + oneLine(diary.getTitle()));
        lines.add("");
        if (StringUtils.hasText(diary.getSummary())) {
            lines.add("> " + oneLine(diary.getSummary()));
            lines.add("");
        }
        lines.add(block(diary.getContent()));
        appendLinks(lines, "这次沉淀出的经验", experienceLinks);
        appendLinks(lines, "关联的知识", knowledgeLinks);
        return String.join("\n", lines);
    }

    /** 经验：问题 / 原因 / 方案 / 经验四段，外链来源日记与相关知识。 */
    public static String experience(Experience experience, List<String> tags,
                                    String diaryLink, List<String> knowledgeLinks) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("type: experience");
        lines.add("domain: " + quote(experience.getDomain()));
        lines.add("memoagent-id: " + experience.getId());
        lines.add("created: " + quote(format(experience.getCreatedAt())));
        lines.add("updated: " + quote(format(experience.getUpdatedAt())));
        lines.addAll(tagLines(tags));
        lines.add("---");
        lines.add("");
        lines.add("# " + oneLine(experience.getTitle()));
        lines.add("");
        section(lines, "问题", experience.getProblem());
        section(lines, "原因", experience.getCause());
        section(lines, "解决方案", experience.getSolution());
        section(lines, "经验", experience.getLesson());
        if (StringUtils.hasText(diaryLink)) {
            appendLinks(lines, "来源日记", List.of(diaryLink));
        }
        appendLinks(lines, "关联的知识", knowledgeLinks);
        return String.join("\n", lines);
    }

    /** 知识：摘要 + 正文 + 相关知识双链。 */
    public static String knowledge(Knowledge knowledge, List<String> tags, List<String> relatedLinks) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("type: knowledge");
        lines.add("category: " + quote(knowledge.getCategory()));
        lines.add("domain: " + quote(knowledge.getDomain()));
        lines.add("mastery: " + quote(knowledge.getMastery()));
        lines.add("memoagent-id: " + knowledge.getId());
        lines.add("created: " + quote(format(knowledge.getCreatedAt())));
        lines.add("updated: " + quote(format(knowledge.getUpdatedAt())));
        lines.addAll(tagLines(tags));
        lines.add("---");
        lines.add("");
        lines.add("# " + oneLine(knowledge.getTitle()));
        lines.add("");
        if (StringUtils.hasText(knowledge.getSummary())) {
            lines.add("> " + oneLine(knowledge.getSummary()));
            lines.add("");
        }
        lines.add(block(knowledge.getContent()));
        appendLinks(lines, "相关知识", relatedLinks);
        return lines.size() > 0 ? String.join("\n", lines) : "";
    }

    /**
     * 文件名安全化：剔除非法字符、压缩空白、截断到 60 字。
     * 返回空串表示标题没有可用字符，由调用方退化到 id。
     */
    public static String safeFileName(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String name = title.replaceAll(ILLEGAL, " ")
                .replaceAll("\\s+", " ")
                .trim();
        // 去掉结尾的点（Windows 会静默丢弃，导致 OBSIDIAN 里文件名对不上双链）
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        if (name.length() > FILE_NAME_MAX) {
            name = name.substring(0, FILE_NAME_MAX).trim();
        }
        return name;
    }

    /** 双链语法。空值/空串返回 null，调用方负责过滤。 */
    public static String link(String noteName) {
        return StringUtils.hasText(noteName) ? "[[" + noteName + "]]" : null;
    }

    /** 导出的包根目录说明：让用户在 Obsidian 里看到「这是只读镜像」。 */
    public static String readme(LocalDateTime exportedAt, int diaries, int experiences, int knowledge) {
        List<String> lines = new ArrayList<>();
        lines.add("# MemoAgent 导出说明");
        lines.add("");
        lines.add("导出时间：" + DATE_TIME.format(exportedAt));
        lines.add("");
        lines.add("| 类型 | 数量 |");
        lines.add("| --- | --- |");
        lines.add("| 日记 | " + diaries + " |");
        lines.add("| 经验 | " + experiences + " |");
        lines.add("| 知识 | " + knowledge + " |");
        lines.add("");
        lines.add("## 这是单向导出的镜像");
        lines.add("");
        lines.add("这个目录里的笔记由 MemoAgent 生成，内容以 MemoAgent 为准。");
        lines.add("在当前版本中，直接在这里修改笔记不会被同步回 MemoAgent；");
        lines.add("再次导出会覆盖同名文件。要修改内容，请回 MemoAgent 编辑后重新导出。");
        lines.add("");
        lines.add("## 目录");
        lines.add("");
        lines.add("- `Diary/` 日记：`日期 标题` 命名，正文含关联经验 / 知识的双链");
        lines.add("- `Experience/` 经验：问题 / 原因 / 方案 / 经验四段式");
        lines.add("- `Knowledge/` 知识：摘要 + 正文 + 相关知识双链");
        lines.add("");
        lines.add("每篇笔记的 frontmatter 带 `memoagent-id`，可用于对回源记录。");
        return String.join("\n", lines);
    }

    private static void section(List<String> lines, String heading, String body) {
        if (!StringUtils.hasText(body)) {
            return;
        }
        lines.add("## " + heading);
        lines.add("");
        lines.add(block(body));
    }

    private static void appendLinks(List<String> lines, String heading, List<String> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        lines.add("## " + heading);
        lines.add("");
        for (String link : links) {
            lines.add("- " + link);
        }
        lines.add("");
    }

    private static List<String> tagLines(List<String> tags) {
        List<String> lines = new ArrayList<>();
        List<String> clean = tags == null ? List.of()
                : tags.stream().filter(StringUtils::hasText).distinct().toList();
        if (clean.isEmpty()) {
            lines.add("tags: []");
            return lines;
        }
        lines.add("tags:");
        for (String tag : clean) {
            lines.add("  - " + quote(tag));
        }
        return lines;
    }

    /** YAML 字符串值：双引号包裹，内部双引号和反斜杠转义。 */
    private static String quote(String value) {
        if (!StringUtils.hasText(value)) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ") + "\"";
    }

    /** 标题/摘要必须单行：换行会把 Markdown 结构撑破。 */
    private static String oneLine(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ").trim();
    }

    /** 正文原样保留换行；null 退化为空串，避免 String.join 出现 "null"。 */
    private static String block(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : DATE_TIME.format(time);
    }

    private static String origin(String origin) {
        if (!StringUtils.hasText(origin)) {
            return "manual";
        }
        return origin;
    }
}
