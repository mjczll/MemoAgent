package com.memoagent.vo;

import com.memoagent.dto.KnowledgeDraftRequest;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 待确认的经验候选。JSON 字段在服务层已还原成结构化对象，前端不用自己解。 */
@Data
public class DiaryCandidateVO {

    private Long id;

    private Long diaryId;

    private String title;

    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    private String domain;

    private List<String> tags = new ArrayList<>();

    /** 可随经验一起写入的知识候选，可为空 */
    private KnowledgeDraftRequest knowledge;
}
