package com.memoagent.service;

import com.memoagent.vo.TodoVO;

import java.util.List;

/** 待办通知：查询式动态计算（不落库、无定时任务）——到点与否在每次查询时判定。 */
public interface TodoService {

    /** 当前用户的待办列表：草稿待确认 + 每周回顾待生成（到点且有新内容时）。 */
    List<TodoVO> getTodos();
}
