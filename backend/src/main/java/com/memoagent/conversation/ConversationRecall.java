package com.memoagent.conversation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.Texts;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ConversationRecall {

    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    private static final List<String> STOP_WORDS = stopWords();

    public List<RecallHit> find(String text) {
        List<String> keywords = keywords(text);
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<RecallHit> hits = new ArrayList<>();
        hits.addAll(top(searchDiaries(keywords), keywords));
        hits.addAll(top(searchExperiences(keywords), keywords));
        hits.addAll(top(searchKnowledge(keywords), keywords));
        return hits;
    }

    public static List<String> keywords(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = text.replaceAll("[\\p{Punct}，。！？、；：「」“”‘’\\s]+", "")
                .replace("%", "")
                .replace("_", "");
        List<String> pieces = new ArrayList<>();
        int index = 0;
        while (index < cleaned.length()) {
            String stop = matchStop(cleaned, index);
            if (stop != null) {
                index += stop.length();
                continue;
            }
            int end = index + 1;
            while (end < cleaned.length() && matchStop(cleaned, end) == null) {
                end += 1;
            }
            addPieces(cleaned.substring(index, end), pieces);
            index = end;
        }
        return pieces.stream()
                .filter(piece -> piece.length() >= 2)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(4)
                .toList();
    }

    private List<RecallHit> searchDiaries(List<String> keywords) {
        List<RecallHit> hits = new ArrayList<>();
        for (Diary diary : diaryMapper.selectPage(new Page<>(1, 12), Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getUserId, CurrentUser.id())
                .and(item -> match(item, keywords, Diary::getTitle, Diary::getSummary, Diary::getContent))
                .orderByDesc(Diary::getDiaryDate)).getRecords()) {
            hits.add(new RecallHit("diary", diary.getId(), diary.getTitle(), Texts.truncate(diary.getSummary(), 80)));
        }
        return hits;
    }

    private List<RecallHit> searchExperiences(List<String> keywords) {
        List<RecallHit> hits = new ArrayList<>();
        for (Experience experience : experienceMapper.selectPage(new Page<>(1, 12), Wrappers.<Experience>lambdaQuery()
                .eq(Experience::getUserId, CurrentUser.id())
                .and(item -> match(item, keywords, Experience::getTitle, Experience::getProblem, Experience::getLesson))
                .orderByDesc(Experience::getCreatedAt)).getRecords()) {
            hits.add(new RecallHit("experience", experience.getId(), experience.getTitle(), Texts.truncate(experience.getProblem(), 80)));
        }
        return hits;
    }

    private List<RecallHit> searchKnowledge(List<String> keywords) {
        List<RecallHit> hits = new ArrayList<>();
        for (Knowledge knowledge : knowledgeMapper.selectPage(new Page<>(1, 12), Wrappers.<Knowledge>lambdaQuery()
                .eq(Knowledge::getUserId, CurrentUser.id())
                .and(item -> match(item, keywords, Knowledge::getTitle, Knowledge::getSummary, Knowledge::getContent))
                .orderByDesc(Knowledge::getUpdatedAt)).getRecords()) {
            hits.add(new RecallHit("knowledge", knowledge.getId(), knowledge.getTitle(), Texts.truncate(knowledge.getSummary(), 80)));
        }
        return hits;
    }

    private static <T> void match(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> wrapper,
            List<String> keywords,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> first,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> second,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> third) {
        for (int index = 0; index < keywords.size(); index += 1) {
            String keyword = keywords.get(index);
            if (index > 0) {
                wrapper.or();
            }
            wrapper.like(first, keyword).or().like(second, keyword).or().like(third, keyword);
        }
    }

    private static List<RecallHit> top(List<RecallHit> hits, List<String> keywords) {
        return hits.stream()
                .sorted(Comparator.comparingInt((RecallHit hit) -> score(hit, keywords)).reversed())
                .limit(4)
                .toList();
    }

    private static int score(RecallHit hit, List<String> keywords) {
        String haystack = (hit.title() == null ? "" : hit.title()) + (hit.excerpt() == null ? "" : hit.excerpt());
        int score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                score += keyword.length();
            }
        }
        return score;
    }

    private static void addPieces(String chunk, List<String> pieces) {
        String rest = chunk;
        while (rest.length() >= 2) {
            if (rest.length() <= 4) {
                pieces.add(rest);
                return;
            }
            pieces.add(rest.substring(0, 4));
            rest = rest.substring(4);
        }
    }

    private static String matchStop(String text, int index) {
        for (String stop : STOP_WORDS) {
            if (text.startsWith(stop, index)) {
                return stop;
            }
        }
        return null;
    }

    private static List<String> stopWords() {
        Set<String> words = new LinkedHashSet<>(List.of(
                "今天", "昨天", "前天", "上午", "下午", "晚上", "刚才", "现在", "已经", "还是", "就是", "然后",
                "因为", "所以", "如果", "但是", "不过", "可以", "没有", "不是", "一下", "这个", "那个", "这次",
                "这种", "那种", "什么", "怎么", "如何", "为什么", "我们", "你们", "他们", "自己", "一个", "一些",
                "一直", "直接", "突然", "遇到", "发现", "看到", "查下来", "查到", "处理", "应对", "情况", "时候",
                "问题", "你好", "您好", "在吗",
                "的", "了", "是", "又", "也", "都", "就", "在", "和", "与", "或", "及", "对", "从", "到", "给",
                "让", "而", "且", "并", "很", "太", "最", "更", "还", "再", "我", "你", "他", "她", "它", "们",
                "吗", "呢", "吧", "啊", "被", "把"));
        return words.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }
}
