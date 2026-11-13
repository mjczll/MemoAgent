package com.memoagent.service;

import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.DiaryQuery;
import com.memoagent.dto.DiaryUpdateRequest;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.DiaryListItemVO;
import com.memoagent.vo.PageData;
import com.memoagent.vo.TagCountVO;

import java.util.List;

public interface DiaryService {

    DiaryDetailVO create(DiaryCreateRequest request);

    PageData<DiaryListItemVO> list(DiaryQuery query);

    /** 当前用户日记用到的标签及各自篇数，按篇数从多到少。 */
    List<TagCountVO> tagSummary();

    DiaryDetailVO get(Long id);

    DiaryDetailVO update(Long id, DiaryUpdateRequest request);

    void delete(Long id);
}
