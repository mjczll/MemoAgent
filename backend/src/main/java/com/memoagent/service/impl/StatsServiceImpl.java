package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.Masteries;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.KnowledgeRelated;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.mapper.KnowledgeRelatedMapper;
import com.memoagent.service.StatsService;
import com.memoagent.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeRelatedMapper knowledgeRelatedMapper;

    @Override
    public StatsVO get() {
        long userId = CurrentUser.id();

        QueryWrapper<Diary> diaryWrapper = new QueryWrapper<>();
        diaryWrapper.eq("user_id", userId);
        List<Diary> diaries = diaryMapper.selectList(diaryWrapper);

        QueryWrapper<Experience> experienceWrapper = new QueryWrapper<>();
        experienceWrapper.eq("user_id", userId);
        List<Experience> experiences = experienceMapper.selectList(experienceWrapper);

        QueryWrapper<Knowledge> knowledgeWrapper = new QueryWrapper<>();
        knowledgeWrapper.eq("user_id", userId);
        List<Knowledge> knowledge = knowledgeMapper.selectList(knowledgeWrapper);

        long mastered = knowledge.stream().filter(item -> Masteries.MASTERED.equals(item.getMastery())).count();
        long interviewOrigin = diaries.stream().filter(item -> "interview".equals(item.getOrigin())).count();
        Set<String> domains = new HashSet<>();
        experiences.forEach(item -> {
            if (StringUtils.hasText(item.getDomain())) {
                domains.add(item.getDomain());
            }
        });
        knowledge.forEach(item -> {
            if (StringUtils.hasText(item.getDomain())) {
                domains.add(item.getDomain());
            }
        });

        StatsVO vo = new StatsVO();
        vo.setDiaries(diaries.size());
        vo.setExperiences(experiences.size());
        vo.setKnowledge(knowledge.size());
        vo.setMastered(mastered);
        vo.setMasteryRate(knowledge.isEmpty() ? 0 : (int) Math.round(mastered * 100.0 / knowledge.size()));
        vo.setDomains(domains.size());
        vo.setInterviewOrigin(interviewOrigin);
        vo.setLinkCount(knowledgeRelatedMapper.selectCount(new QueryWrapper<KnowledgeRelated>()));
        return vo;
    }
}
