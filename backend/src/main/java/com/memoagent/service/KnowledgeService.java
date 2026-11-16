package com.memoagent.service;

import com.memoagent.dto.KnowledgeCreateRequest;
import com.memoagent.dto.KnowledgeQuery;
import com.memoagent.dto.KnowledgeUpdateRequest;
import com.memoagent.vo.KnowledgeDetailVO;
import com.memoagent.vo.KnowledgeListItemVO;
import com.memoagent.vo.PageData;

import java.util.List;

public interface KnowledgeService {

    KnowledgeDetailVO create(KnowledgeCreateRequest request);

    PageData<KnowledgeListItemVO> list(KnowledgeQuery query);

    KnowledgeDetailVO get(Long id);

    KnowledgeDetailVO update(Long id, KnowledgeUpdateRequest request);

    void delete(Long id);

    /** 当前用户用过的领域名去重列表（非空），供领域复用提示。 */
    List<String> domainSummary();
}
