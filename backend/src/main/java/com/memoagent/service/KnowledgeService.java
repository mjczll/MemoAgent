package com.memoagent.service;

import com.memoagent.dto.KnowledgeCreateRequest;
import com.memoagent.dto.KnowledgeQuery;
import com.memoagent.dto.KnowledgeUpdateRequest;
import com.memoagent.vo.KnowledgeDetailVO;
import com.memoagent.vo.KnowledgeListItemVO;
import com.memoagent.vo.PageData;

public interface KnowledgeService {

    KnowledgeDetailVO create(KnowledgeCreateRequest request);

    PageData<KnowledgeListItemVO> list(KnowledgeQuery query);

    KnowledgeDetailVO get(Long id);

    KnowledgeDetailVO update(Long id, KnowledgeUpdateRequest request);

    void delete(Long id);
}
