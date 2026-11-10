package com.memoagent.ai;

import com.memoagent.common.DiaryKinds;
import com.memoagent.common.Texts;
import com.memoagent.conversation.DraftSanitizer;
import com.memoagent.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

public final class GenerationSanitizer {

    static final String DEFAULT_DOMAIN = "项目开发";
    private static final String DEFAULT_KIND = "记录";
    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String PENDING = "待补充";
    private static final int MAX_EXPERIENCES = 3;
    private static final int MAX_TAGS = 8;

    private GenerationSanitizer() {
    }

    public static DiaryGeneration sanitizeDiary(DiaryGeneration reply, String source) {
        if (reply == null) {
            throw BusinessException.badRequest("整理结果无效");
        }
        if (DraftSanitizer.omitCommands(source).isBlank()) {
            throw BusinessException.badRequest("没有可以整理的内容");
        }
        String content = DraftSanitizer.omitCommands(reply.getContent());
        if (content.isBlank()) {
            throw BusinessException.badRequest("整理结果缺少正文");
        }
        String title = DraftSanitizer.omitCommands(reply.getTitle());
        if (title.isBlank()) {
            throw BusinessException.badRequest("整理结果缺少标题");
        }
        DiaryGeneration result = new DiaryGeneration();
        result.setTitle(cut(title, 200));
        result.setContent(content);
        String summary = DraftSanitizer.omitCommands(reply.getSummary());
        result.setSummary(summary.isBlank() ? cut(content, 80) : cut(summary, 500));
        String kind = DraftSanitizer.omitCommands(reply.getKind());
        result.setKind(DiaryKinds.normalize(cut(kind.isBlank() ? DEFAULT_KIND : kind, 32)));
        result.setTags(cleanTags(reply.getTags(), source));
        result.setExperiences(sanitizeExperiences(reply.getExperiences(), result.getTitle(), source));
        return result;
    }

    public static List<ExperienceCandidate> sanitizeExperiences(List<ExperienceCandidate> experiences, String diaryTitle,
                                                               String reference) {
        List<ExperienceCandidate> kept = new ArrayList<>();
        String fallbackTitle = diaryTitle == null || diaryTitle.isBlank() ? "经验" : diaryTitle.trim();
        for (ExperienceCandidate source : experiences == null ? List.<ExperienceCandidate>of() : experiences) {
            if (kept.size() == MAX_EXPERIENCES) {
                break;
            }
            ExperienceCandidate cleaned = cleanExperience(source, fallbackTitle, reference);
            if (cleaned != null) {
                kept.add(cleaned);
            }
        }
        return kept;
    }

    private static ExperienceCandidate cleanExperience(ExperienceCandidate source, String diaryTitle, String reference) {
        if (source == null) {
            return null;
        }
        String problem = field(source.getProblem());
        String cause = field(source.getCause());
        String solution = field(source.getSolution());
        String lesson = field(source.getLesson());
        if (problem.isBlank() || (cause.isBlank() && solution.isBlank() && lesson.isBlank())) {
            return null;
        }
        ExperienceCandidate experience = new ExperienceCandidate();
        String title = field(source.getTitle());
        experience.setTitle(cut(title.isBlank() ? diaryTitle + " —— 经验提炼" : title, 200));
        experience.setProblem(problem);
        experience.setCause(cause);
        experience.setSolution(solution);
        experience.setLesson(lesson);
        String domain = field(source.getDomain());
        experience.setDomain(domain.isBlank() ? DEFAULT_DOMAIN : cut(domain, 64));
        experience.setTags(cleanTags(source.getTags(), reference));
        experience.setKnowledge(cleanKnowledge(source.getKnowledge(), reference));
        return experience;
    }

    private static KnowledgeCandidate cleanKnowledge(KnowledgeCandidate source, String reference) {
        if (source == null) {
            return null;
        }
        String title = field(source.getTitle());
        String content = field(source.getContent());
        if (title.isBlank() || content.isBlank()) {
            return null;
        }
        KnowledgeCandidate knowledge = new KnowledgeCandidate();
        knowledge.setTitle(cut(title, 200));
        knowledge.setContent(content);
        String domain = field(source.getDomain());
        knowledge.setDomain(domain.isBlank() ? DEFAULT_DOMAIN : cut(domain, 64));
        String category = field(source.getCategory());
        knowledge.setCategory(category.isBlank() ? DEFAULT_CATEGORY : cut(category, 64));
        String summary = field(source.getSummary());
        knowledge.setSummary(summary.isBlank() ? cut(content, 80) : cut(summary, 500));
        knowledge.setTags(cleanTags(source.getTags(), reference));
        return knowledge;
    }

    private static String field(String text) {
        String cleaned = DraftSanitizer.omitCommands(text);
        if (PENDING.equals(cleaned)) {
            return "";
        }
        return cleaned;
    }

    private static List<String> cleanTags(List<String> tags, String reference) {
        List<String> cleaned = new ArrayList<>();
        for (String tag : Texts.modelTags(tags, reference)) {
            String value = cut(DraftSanitizer.omitCommands(tag), 32);
            if (value.isBlank() || PENDING.equals(value) || cleaned.contains(value)) {
                continue;
            }
            cleaned.add(value);
            if (cleaned.size() == MAX_TAGS) {
                break;
            }
        }
        return cleaned;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
