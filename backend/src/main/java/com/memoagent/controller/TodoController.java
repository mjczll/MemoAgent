package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.TodoService;
import com.memoagent.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 待办通知：查询式动态计算。 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public Result<List<TodoVO>> getTodos() {
        return Result.ok(todoService.getTodos());
    }
}
