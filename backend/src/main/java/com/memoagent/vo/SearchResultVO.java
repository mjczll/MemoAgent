package com.memoagent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义检索结果。available 单独标出来，是为了让调用方分清
 * 「没命中」和「向量库没开 / 模型不可用」这两件完全不同的事。
 */
@Data
public class SearchResultVO {

    private List<SearchHitVO> hits = new ArrayList<>();

    private boolean available;

    private String notice;

    public static SearchResultVO ok(List<SearchHitVO> hits) {
        SearchResultVO result = new SearchResultVO();
        result.setHits(hits);
        result.setAvailable(true);
        return result;
    }

    public static SearchResultVO empty() {
        return ok(new ArrayList<>());
    }

    public static SearchResultVO unavailable(String notice) {
        SearchResultVO result = new SearchResultVO();
        result.setAvailable(false);
        result.setNotice(notice);
        return result;
    }
}
