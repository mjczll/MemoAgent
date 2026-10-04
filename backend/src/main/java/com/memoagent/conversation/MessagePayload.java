package com.memoagent.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessagePayload {

    /** accept / skip，仅模型补充使用 */
    private String choice;

    /** 这条助手回复结束后可以整理 */
    private boolean ready;

    private SupplementPayload supplement;

    private List<RefPayload> refs = new ArrayList<>();

    private List<LinkPayload> links = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupplementPayload {
        private String title;
        private String domain;
        private String category;
        private String summary;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RefPayload {
        private String type;
        private Long id;
        private String title;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkPayload {
        private Long knowledgeId;
        private String title;
        /** accept / skip */
        private String choice;
    }
}
