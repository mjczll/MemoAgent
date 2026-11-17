package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.TodoService;
import com.memoagent.service.TodoStreamService;
import com.memoagent.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 待办通知：查询式动态计算。 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final TodoStreamService todoStreamService;

    @GetMapping
    public Result<List<TodoVO>> getTodos() {
        return Result.ok(todoService.getTodos());
    }

    /** 待办变更推送：收到 todos-changed 事件后前端拉一次 GET /api/todos 全量。 */
    @GetMapping("/stream")
    public SseEmitter stream() {
        return todoStreamService.subscribe();
    }
}
