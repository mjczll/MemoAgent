package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.memoagent.common.CurrentUser;
import com.memoagent.entity.Diary;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.service.DiaryKindService;
import com.memoagent.vo.DiaryKindVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiaryKindServiceImpl implements DiaryKindService {

    private final DiaryMapper diaryMapper;

    @Override
    public List<DiaryKindVO> list() {
        QueryWrapper<Diary> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", CurrentUser.id());
        List<Diary> rows = diaryMapper.selectList(wrapper);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Diary diary : rows) {
            if (!StringUtils.hasText(diary.getKind())) {
                continue;
            }
            counts.merge(diary.getKind().trim(), 1L, Long::sum);
        }

        List<Map.Entry<String, Long>> ranked = counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        String defaultName = ranked.isEmpty() ? null : ranked.getFirst().getKey();
        List<DiaryKindVO> result = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, Long> entry : ranked) {
            DiaryKindVO vo = new DiaryKindVO();
            vo.setId(entry.getKey());
            vo.setName(entry.getKey());
            vo.setSortOrder(order++);
            vo.setDefault(entry.getKey().equals(defaultName));
            vo.setDiaryCount(entry.getValue());
            result.add(vo);
        }
        return result;
    }
}
