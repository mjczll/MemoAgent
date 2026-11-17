package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.entity.Conversation;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Profile;
import com.memoagent.mapper.ConversationMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ProfileMapper;
import com.memoagent.service.TodoService;
import com.memoagent.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 待办通知：查询式动态计算——不落库、无定时任务。
 * 每次查询时判定条件是否满足，因此「错过设定时间」不存在：下一次查询（打开待办小窗、切换路由）即补上。
 */
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    /** 自动生成的周回顾会话标题前缀，同时用作幂等判定 */
    public static final String AUTO_REVIEW_TITLE_PREFIX = "本周回顾";
    private static final LocalTime DEFAULT_REVIEW_TIME = LocalTime.of(21, 30);

    private final ProfileMapper profileMapper;
    private final ConversationMapper conversationMapper;
    private final DiaryMapper diaryMapper;

    @Override
    public List<TodoVO> getTodos() {
        Long userId = CurrentUser.id();
        List<TodoVO> todos = new ArrayList<>();

        Long draftCount = conversationMapper.selectCount(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, "drafted"));
        if (draftCount != null && draftCount > 0) {
            TodoVO todo = new TodoVO();
            todo.setType("drafts");
            todo.setTitle(draftCount + " 份草稿待确认");
            todo.setDescription("AI 生成的草稿确认提交后才会保存为日记 / 经验");
            todo.setCount(draftCount);
            todos.add(todo);
        }

        TodoVO reviewTodo = weeklyReviewTodo(userId);
        if (reviewTodo != null) {
            todos.add(reviewTodo);
        }
        return todos;
    }

    /** 每周回顾待生成：开关开 + 已过本周触发时刻 + 本周未生成 + 本周有新内容。 */
    private TodoVO weeklyReviewTodo(Long userId) {
        Profile profile = profileMapper.selectById(userId);
        if (profile == null || !Boolean.TRUE.equals(profile.getWeeklyReview())) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime trigger = triggerMoment(profile, weekStart);
        if (LocalDateTime.now().isBefore(trigger)) {
            return null;
        }
        if (autoReviewExistsThisWeek(userId, weekStart)) {
            return null;
        }
        Long newDiaries = diaryMapper.selectCount(Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, weekStart.atStartOfDay()));
        if (newDiaries == null || newDiaries == 0) {
            return null;
        }

        TodoVO todo = new TodoVO();
        todo.setType("weekly-review");
        todo.setTitle("本周回顾待生成");
        todo.setDescription("计划时间 " + dayName(profile.getReviewDay()) + " " + trigger.toLocalTime()
                + " · 本周新增 " + newDiaries + " 篇日记");
        todo.setScheduledAt(trigger.toString());
        todo.setCount(newDiaries);
        return todo;
    }

    /** 本周触发时刻：自然周（周一起）内的第 reviewDay 天的 reviewTime。reviewDay：0=周日 ... 6=周六。 */
    private LocalDateTime triggerMoment(Profile profile, LocalDate weekStart) {
        int day = profile.getReviewDay() == null ? 0 : Math.max(0, Math.min(profile.getReviewDay(), 6));
        LocalDate triggerDate = weekStart.plusDays((day + 6) % 7);
        return LocalDateTime.of(triggerDate, reviewTime(profile.getReviewTime()));
    }

    private LocalTime reviewTime(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalTime.parse(raw.trim());
            } catch (DateTimeParseException ignored) {
                // 落回默认时间
            }
        }
        return DEFAULT_REVIEW_TIME;
    }

    private String dayName(Integer day) {
        int d = day == null ? 0 : Math.max(0, Math.min(day, 6));
        String[] names = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return names[d];
    }

    /** 本周是否已存在自动生成的周回顾会话（标题前缀 + 创建时间判定），也供生成接口做幂等守卫。 */
    public boolean autoReviewExistsThisWeek(Long userId, LocalDate weekStart) {
        Long count = conversationMapper.selectCount(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .likeRight(Conversation::getTitle, AUTO_REVIEW_TITLE_PREFIX)
                .ge(Conversation::getCreatedAt, weekStart.atStartOfDay()));
        return count != null && count > 0;
    }
}
