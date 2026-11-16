package com.memoagent.service;

import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.ExperienceQuery;
import com.memoagent.dto.ExperienceUpdateRequest;
import com.memoagent.vo.ExperienceDetailVO;
import com.memoagent.vo.ExperienceListItemVO;
import com.memoagent.vo.PageData;

import java.util.List;

public interface ExperienceService {

    ExperienceDetailVO create(ExperienceCreateRequest request);

    PageData<ExperienceListItemVO> list(ExperienceQuery query);

    ExperienceDetailVO get(Long id);

    ExperienceDetailVO update(Long id, ExperienceUpdateRequest request);

    void attachToDiary(Long diaryId, List<Long> experienceIds);

    /** 当前用户用过的领域名去重列表（非空），供领域复用提示与草稿页下拉。 */
    List<String> domainSummary();
}
