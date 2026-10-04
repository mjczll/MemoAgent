package com.memoagent.conversation;

import com.memoagent.common.Texts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DraftSanitizer {

    static final List<String> COMMANDS = List.of(
            "不帮我整理一下吗",
            "整理成日记",
            "整理成记录",
            "关联到这次",
            "先不关联",
            "写入知识库",
            "帮我整理",
            "整理一下",
            "就这样吧",
            "可以整理",
            "就这些",
            "两个都要",
            "先不写"
    ).stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private DraftSanitizer() {
    }

    public static List<String> storyLines(List<String> userLines) {
        if (userLines == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : userLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String story = omitCommands(line.trim());
            if (!story.isBlank()) {
                lines.add(story);
            }
        }
        return lines;
    }

    public static StoredDraft sanitize(DraftReply reply, List<String> userLines) {
        DraftReply source = reply == null ? new DraftReply() : reply;
        List<String> lines = storyLines(userLines);
        String joined = String.join("\n\n", lines);
        StoredDraft draft = new StoredDraft();
        draft.setIncident(source.isIncident() && !lines.isEmpty());
        draft.setDate(LocalDate.now().toString());
        draft.setTitle(firstText(omitCommands(source.getTitle()), lines.isEmpty() ? "一次对话" : Texts.truncate(lines.get(0), 40)));
        draft.setKind(firstText(omitCommands(source.getKind()), "记录"));
        draft.setDomain(source.getDomain() == null ? "" : Texts.truncate(omitCommands(source.getDomain()), 64));
        draft.setTags(cleanTags(source.getTags()));
        draft.setSummary(firstText(omitCommands(source.getSummary()), Texts.truncate(joined, 60)));
        draft.setContent(firstText(omitCommands(source.getContent()), joined));
        draft.setProblem(firstText(omitCommands(source.getProblem()), lineAt(lines, 2)));
        draft.setCause(firstText(omitCommands(source.getCause()), lineAt(lines, 1)));
        draft.setSolution(firstText(omitCommands(source.getSolution()), lineAt(lines, 3)));
        draft.setLesson(firstText(omitCommands(source.getLesson()), lineAt(lines, lines.size() - 1)));
        if (!draft.isIncident()) {
            draft.setContent("");
            draft.setProblem("");
            draft.setCause("");
            draft.setSolution("");
            draft.setLesson("");
        }
        return draft;
    }

    static String omitCommands(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.replaceFirst("^\\s*\\d+[.、．]\\s*", "").trim();
            String story = stripClauses(line);
            if (!story.isBlank()) {
                kept.add(story);
            }
        }
        return String.join("\n", kept).trim();
    }

    private static String stripClauses(String text) {
        String[] parts = text.split("[，。！？；]");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            String piece = stripEdges(part.trim());
            if (!piece.isBlank()) {
                kept.add(piece);
            }
        }
        return String.join("，", kept).trim();
    }

    private static String stripEdges(String clause) {
        if (commandOnly(clause)) {
            return "";
        }
        String result = clause;
        for (String command : COMMANDS) {
            if (result.startsWith(command)) {
                result = result.substring(command.length());
            }
            if (result.endsWith(command)) {
                result = result.substring(0, result.length() - command.length());
            }
        }
        return result.replaceAll("^[，。！？；、\\s]+", "").replaceAll("[，。！？；、\\s]+$", "").trim();
    }

    private static boolean commandOnly(String text) {
        String normalized = text.replaceAll("[\\s\\p{Punct}，。！？、；：「」“”‘’]", "");
        if (normalized.isEmpty()) {
            return false;
        }
        String rest = normalized;
        boolean hit = false;
        for (String command : COMMANDS) {
            if (rest.contains(command)) {
                rest = rest.replace(command, "");
                hit = true;
            }
        }
        rest = rest.replaceAll("[不吗呢吧啊了请要的一下]", "");
        return hit && rest.isEmpty();
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Texts.normalizeTags(tags.stream().filter(tag -> !commandOnly(tag)).toList()));
    }

    private static String lineAt(List<String> lines, int index) {
        if (lines.isEmpty()) {
            return "待补充";
        }
        int safe = Math.max(0, Math.min(index, lines.size() - 1));
        return lines.get(safe);
    }

    private static String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback;
    }
}
