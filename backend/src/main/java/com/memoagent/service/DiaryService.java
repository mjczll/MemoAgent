package com.memoagent.service;

import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.DiaryQuery;
import com.memoagent.dto.DiaryUpdateRequest;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.DiaryListItemVO;
import com.memoagent.vo.PageData;

public interface DiaryService {

    DiaryDetailVO create(DiaryCreateRequest request);

    PageData<DiaryListItemVO> list(DiaryQuery query);

    DiaryDetailVO get(Long id);

    DiaryDetailVO update(Long id, DiaryUpdateRequest request);

    void delete(Long id);
}
