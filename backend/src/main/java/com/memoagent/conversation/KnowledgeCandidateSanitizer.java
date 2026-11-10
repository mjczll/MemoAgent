package com.memoagent.conversation;

import com.memoagent.ai.KnowledgeCandidate;
import com.memoagent.common.Texts;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把模型给的知识候选收紧成可入库的形状。
 * 判定只认用户原话：evidence 归一化后必须落在某一条用户发言之内，对不上就丢弃。
 */
public final class KnowledgeCandidateSanitizer {

    static final int MIN_CONTENT = 40;
    static final int MIN_EVIDENCE = 6;
    static final int MAX_CANDIDATES = 3;
    static final int MAX_TAGS = 5;

    private static final int MAX_TITLE = 120;
    private static final int MAX_SUMMARY = 500;
    private static final int MAX_DOMAIN = 64;
    private static final int MAX_CATEGORY = 64;
    private static final int MAX_TAG = 32;

    private KnowledgeCandidateSanitizer() {
    }

    public static List<StoredKnowledgeCandidate> sanitize(List<KnowledgeCandidate> raw, List<String> storyLines) {
        if (raw == null || raw.isEmpty() || storyLines == null || storyLines.isEmpty()) {
            return List.of();
        }
        List<String> normalizedLines = new ArrayList<>();
        for (String line : storyLines) {
            String normalized = normalize(line);
            if (!normalized.isBlank()) {
                normalizedLines.add(normalized);
            }
        }
        String reference = String.join("\n", storyLines);
        List<StoredKnowledgeCandidate> kept = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (KnowledgeCandidate item : raw) {
            if (item == null) {
                continue;
            }
            if (kept.size() >= MAX_CANDIDATES) {
                break;
            }
            StoredKnowledgeCandidate candidate = convert(item, normalizedLines, reference);
            if (candidate == null) {
                continue;
            }
            String key = normalize(candidate.getTitle());
            if (key.isBlank() || !titles.add(key)) {
                continue;
            }
            kept.add(candidate);
        }
        return kept;
    }

    private static StoredKnowledgeCandidate convert(KnowledgeCandidate item, List<String> normalizedLines, String reference) {
        String title = clean(item.getTitle(), MAX_TITLE);
        String content = clean(item.getContent(), Integer.MAX_VALUE);
        if (title.isBlank() || content.length() < MIN_CONTENT) {
            return null;
        }
        String evidence = item.getEvidence() == null ? "" : item.getEvidence().trim();
        if (!supported(evidence, normalizedLines)) {
            return null;
        }
        StoredKnowledgeCandidate candidate = new StoredKnowledgeCandidate();
        candidate.setTitle(title);
        candidate.setDomain(clean(item.getDomain(), MAX_DOMAIN));
        candidate.setCategory(clean(item.getCategory(), MAX_CATEGORY));
        String summary = clean(item.getSummary(), MAX_SUMMARY);
        candidate.setSummary(summary.isBlank() ? Texts.truncate(content, 80) : summary);
        candidate.setContent(content);
        candidate.setTags(cleanTags(item.getTags(), reference));
        candidate.setEvidence(evidence);
        return candidate;
    }

    /** evidence 去掉空白和标点后，必须完整落在某一条用户发言里面。 */
    static boolean supported(String evidence, List<String> normalizedLines) {
        String target = normalize(evidence);
        if (target.length() < MIN_EVIDENCE) {
            return false;
        }
        for (String line : normalizedLines) {
            if (line.contains(target)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String base = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i += 1) {
            char ch = base.charAt(i);
            if (Character.isWhitespace(ch) || isPunctuation(ch)) {
                continue;
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    private static boolean isPunctuation(char ch) {
        int type = Character.getType(ch);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static String clean(String text, int limit) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isBlank() || DraftSanitizer.commandOnly(trimmed)) {
            return "";
        }
        return limit == Integer.MAX_VALUE ? trimmed : Texts.truncate(trimmed, limit);
    }

    private static List<String> cleanTags(List<String> tags, String reference) {
        if (tags == null) {
            return new ArrayList<>();
        }
        List<String> kept = new ArrayList<>();
        for (String tag : Texts.modelTags(tags, reference)) {
            String value = clean(tag, MAX_TAG);
            if (value.isBlank() || kept.contains(value)) {
                continue;
            }
            kept.add(value);
            if (kept.size() >= MAX_TAGS) {
                break;
            }
        }
        return kept;
    }
}
