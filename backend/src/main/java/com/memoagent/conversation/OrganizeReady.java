package com.memoagent.conversation;

import java.util.List;

public final class OrganizeReady {

    private static final String[] EXPLICIT = {
            "就这些",
            "就这样吧",
            "帮我整理",
            "整理一下",
            "可以整理",
            "整理成日记",
            "整理成记录",
    };

    private static final String[] SPOKEN = {
            "整理成",
            "帮你整理",
            "要不要整理",
            "可以整理",
    };

    private OrganizeReady() {
    }

    public static boolean allow(String kind, boolean modelReady, List<String> userTexts) {
        if (explicit(latest(userTexts))) {
            return true;
        }
        // 复盘：助手已经调过取数工具、正文也写出来了，放行「整理」按钮。
        // 不能套 storyReady——复盘对话里用户只问了一句「这个月做了什么」，字数不达标。
        if (TurnSanitizer.REVIEW.equals(kind)) {
            return true;
        }
        if (TurnSanitizer.SUPPLEMENT.equals(kind) || TurnSanitizer.RECALL.equals(kind)) {
            return false;
        }
        if (!modelReady) {
            return false;
        }
        return storyReady(userTexts);
    }

    static boolean storyReady(List<String> userTexts) {
        int substantive = 0;
        int chars = 0;
        if (userTexts == null) {
            return false;
        }
        for (String text : userTexts) {
            if (text == null) {
                continue;
            }
            String normalized = text.replaceAll("\\s+", "");
            if (normalized.isEmpty() || isGreeting(normalized)) {
                continue;
            }
            substantive += 1;
            chars += normalized.length();
        }
        return substantive >= 2 || chars >= 80;
    }

    private static boolean isGreeting(String normalized) {
        String lower = normalized.toLowerCase();
        return lower.equals("你好") || lower.equals("您好") || lower.equals("在吗")
                || lower.equals("hi") || lower.equals("hello");
    }

    private static String latest(List<String> userTexts) {
        if (userTexts == null || userTexts.isEmpty()) {
            return "";
        }
        String latest = userTexts.get(userTexts.size() - 1);
        return latest == null ? "" : latest;
    }

    public static boolean spokenOffer(String assistantText) {
        return containsAny(assistantText, SPOKEN);
    }

    static boolean explicit(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsAny(text, EXPLICIT);
    }

    private static boolean containsAny(String text, String[] phrases) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        for (String phrase : phrases) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
