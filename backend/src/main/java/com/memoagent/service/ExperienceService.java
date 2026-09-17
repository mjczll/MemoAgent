package com.memoagent.service;

import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.ExperienceQuery;
import com.memoagent.dto.ExperienceUpdateRequest;
import com.memoagent.vo.ExperienceDetailVO;
import com.memoagent.vo.ExperienceListItemVO;
import com.memoagent.vo.PageData;

public interface ExperienceService {

    ExperienceDetailVO create(ExperienceCreateRequest request);

    PageData<ExperienceListItemVO> list(ExperienceQuery query);

    ExperienceDetailVO get(Long id);

    ExperienceDetailVO update(Long id, ExperienceUpdateRequest request);
}
