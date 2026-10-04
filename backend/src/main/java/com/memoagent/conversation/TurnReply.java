package com.memoagent.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnReply {

    private String kind;

    private String text;

    /** 这次回复之后，是否可以请用户整理成记录 */
    private boolean ready;

    private String supplementTitle;

    private String supplementDomain;

    private String supplementCategory;

    private String supplementSummary;

    private String supplementContent;

    private List<Long> linkKnowledgeIds = new ArrayList<>();

    private List<Long> refDiaryIds = new ArrayList<>();

    private List<Long> refExperienceIds = new ArrayList<>();

    private List<Long> refKnowledgeIds = new ArrayList<>();
}
