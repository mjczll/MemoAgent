package com.memoagent.common;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

public final class Texts {

    /** 模型经常给标签加 # 前缀。 */
    private static final Pattern TAG_HASH = Pattern.compile("^[#＃]+\\s*");

    /** 只区分实际会互相串的中日韩文字。拉丁字母、数字、符号不参与判断。 */
    private enum Script {
        HAN,
        KANA,
        HANGUL
    }

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

    /**
     * 模型产出的标签专用清洗，语言跟随用户原文，不绑定中文。
     * 先去掉 # 前缀；再检查标签用的文字是否和用户原文用过的文字一致，
     * 例如用户全篇中文却给出日文标签、用户全篇英文却给出中文标签，都算语言漂移。
     * 只要有一条标签漂移，就认为整份标签不可信，全部丢弃 ——
     * 日文标签常混纯汉字写法（会议在日文里写作会議），逐条过滤会漏掉它们，
     * 留下半份看不懂的标签比没有更难处理。
     * 用户手填的标签不走这里。原文为空时无法判断，只做 # 前缀与去重。
     */
    public static List<String> modelTags(List<String> tags, String reference) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        boolean judgeLanguage = reference != null && !reference.isBlank();
        EnumSet<Script> allowed = judgeLanguage ? scriptsOf(reference) : EnumSet.noneOf(Script.class);
        List<String> kept = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = TAG_HASH.matcher(tag.trim()).replaceFirst("").trim();
            if (value.isBlank()) {
                continue;
            }
            if (judgeLanguage && !fitsLanguage(value, allowed)) {
                return List.of();
            }
            if (!kept.contains(value)) {
                kept.add(value);
            }
        }
        return kept;
    }

    /** 标签里出现的文字，必须都在用户原文用过的文字里。 */
    private static boolean fitsLanguage(String tag, EnumSet<Script> allowed) {
        for (int i = 0; i < tag.length(); i += 1) {
            Script script = scriptOf(tag.charAt(i));
            if (script != null && !allowed.contains(script)) {
                return false;
            }
        }
        return true;
    }

    private static EnumSet<Script> scriptsOf(String text) {
        EnumSet<Script> scripts = EnumSet.noneOf(Script.class);
        for (int i = 0; i < text.length(); i += 1) {
            Script script = scriptOf(text.charAt(i));
            if (script != null) {
                scripts.add(script);
            }
        }
        return scripts;
    }

    private static Script scriptOf(char ch) {
        if ((ch >= '\u3040' && ch <= '\u30FF') || (ch >= '\uFF65' && ch <= '\uFF9F')) {
            return Script.KANA;
        }
        if ((ch >= '\u1100' && ch <= '\u11FF') || (ch >= '\u3130' && ch <= '\u318F')
                || (ch >= '\uAC00' && ch <= '\uD7AF')) {
            return Script.HANGUL;
        }
        if ((ch >= '\u3400' && ch <= '\u4DBF') || (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\uF900' && ch <= '\uFAFF')) {
            return Script.HAN;
        }
        return null;
    }
}
