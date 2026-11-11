package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.SemanticSearchService;
import com.memoagent.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** GET /api/search?q=&kinds=diary,experience,knowledge —— 阶段 6 的语义检索入口。 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SemanticSearchService semanticSearchService;

    @GetMapping
    public Result<SearchResultVO> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String kinds,
                                         @RequestParam(required = false) Integer topK,
                                         @RequestParam(required = false) Double minScore) {
        return Result.ok(semanticSearchService.search(q, parseKinds(kinds), topK, minScore));
    }

    private List<String> parseKinds(String kinds) {
        if (kinds == null || kinds.isBlank()) {
            return null;
        }
        return Arrays.stream(kinds.split(",")).map(String::trim).toList();
    }
}
