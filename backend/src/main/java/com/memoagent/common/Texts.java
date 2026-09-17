package com.memoagent.common;

import java.util.List;

public final class Texts {

    private Texts() {
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max) + "…";
    }

    public static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
