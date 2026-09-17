package com.memoagent.service;

import com.memoagent.dto.DiaryKindCreateRequest;
import com.memoagent.dto.DiaryKindReorderRequest;
import com.memoagent.dto.DiaryKindUpdateRequest;
import com.memoagent.entity.DiaryKind;
import com.memoagent.vo.DiaryKindVO;

import java.util.List;

public interface DiaryKindService {

    List<DiaryKindVO> list();

    DiaryKindVO create(DiaryKindCreateRequest request);

    DiaryKindVO update(Long id, DiaryKindUpdateRequest request);

    void delete(Long id);

    List<DiaryKindVO> reorder(DiaryKindReorderRequest request);

    DiaryKind requireByName(String name);
}
