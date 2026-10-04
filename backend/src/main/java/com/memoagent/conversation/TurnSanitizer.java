package com.memoagent.conversation;

import com.memoagent.common.Texts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TurnSanitizer {

    public static final String INTERVIEW = "interview";
    public static final String RECALL = "recall";
    public static final String SUPPLEMENT = "supplement";
    public static final String ASSOCIATION = "association";

    private TurnSanitizer() {
    }

    public static SanitizedTurn finish(TurnReply reply, List<RecallHit> hits) {
        TurnReply source = reply == null ? new TurnReply() : reply;
        String kind = normalizeKind(source.getKind());
        String text = source.getText() == null ? "" : source.getText().trim();
        if (text.isBlank()) {
            text = "我还需要你再讲一句，才能接着问。";
            kind = INTERVIEW;
        }

        Set<Long> diaryIds = idsOf(hits, "diary");
        Set<Long> experienceIds = idsOf(hits, "experience");
        Set<Long> knowledgeIds = idsOf(hits, "knowledge");

        MessagePayload payload = new MessagePayload();
        List<MessagePayload.RefPayload> refs = refs(source, hits, diaryIds, experienceIds, knowledgeIds);
        payload.setRefs(refs);

        if (SUPPLEMENT.equals(kind)) {
            MessagePayload.SupplementPayload supplement = supplement(source);
            if (supplement == null) {
                kind = INTERVIEW;
            } else {
                payload.setSupplement(supplement);
            }
        }

        if (ASSOCIATION.equals(kind)) {
            List<MessagePayload.LinkPayload> links = links(source.getLinkKnowledgeIds(), hits, knowledgeIds);
            if (links.isEmpty()) {
                kind = refs.isEmpty() ? INTERVIEW : RECALL;
            } else {
                payload.setLinks(links);
            }
        }

        if (!SUPPLEMENT.equals(kind)) {
            payload.setSupplement(null);
        }
        if (!ASSOCIATION.equals(kind)) {
            payload.setLinks(List.of());
        }
        if (!RECALL.equals(kind) && !ASSOCIATION.equals(kind)) {
            payload.setRefs(List.of());
        }
        return new SanitizedTurn(kind, text, payload);
    }

    private static String normalizeKind(String kind) {
        if (RECALL.equals(kind) || SUPPLEMENT.equals(kind) || ASSOCIATION.equals(kind)) {
            return kind;
        }
        return INTERVIEW;
    }

    private static Set<Long> idsOf(List<RecallHit> hits, String type) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RecallHit hit : hits) {
            if (type.equals(hit.type()) && hit.id() != null) {
                ids.add(hit.id());
            }
        }
        return ids;
    }

    private static MessagePayload.SupplementPayload supplement(TurnReply reply) {
        if (reply.getSupplementTitle() == null || reply.getSupplementTitle().isBlank()
                || reply.getSupplementContent() == null || reply.getSupplementContent().isBlank()) {
            return null;
        }
        MessagePayload.SupplementPayload supplement = new MessagePayload.SupplementPayload();
        supplement.setTitle(Texts.truncate(reply.getSupplementTitle(), 200));
        supplement.setDomain(reply.getSupplementDomain() == null ? "" : Texts.truncate(reply.getSupplementDomain(), 64));
        supplement.setCategory(reply.getSupplementCategory() == null || reply.getSupplementCategory().isBlank()
                ? "未分类"
                : Texts.truncate(reply.getSupplementCategory(), 64));
        supplement.setSummary(reply.getSupplementSummary() == null ? "" : Texts.truncate(reply.getSupplementSummary(), 500));
        supplement.setContent(reply.getSupplementContent().trim());
        return supplement;
    }

    private static List<MessagePayload.LinkPayload> links(List<Long> requested, List<RecallHit> hits, Set<Long> allowed) {
        List<MessagePayload.LinkPayload> links = new ArrayList<>();
        if (requested == null) {
            return links;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : requested) {
            if (id == null || !allowed.contains(id) || !seen.add(id)) {
                continue;
            }
            MessagePayload.LinkPayload link = new MessagePayload.LinkPayload();
            link.setKnowledgeId(id);
            link.setTitle(titleOf(hits, "knowledge", id));
            links.add(link);
        }
        return links;
    }

    private static List<MessagePayload.RefPayload> refs(
            TurnReply reply,
            List<RecallHit> hits,
            Set<Long> diaryIds,
            Set<Long> experienceIds,
            Set<Long> knowledgeIds) {
        List<MessagePayload.RefPayload> refs = new ArrayList<>();
        addRefs(refs, hits, "diary", reply.getRefDiaryIds(), diaryIds);
        addRefs(refs, hits, "experience", reply.getRefExperienceIds(), experienceIds);
        addRefs(refs, hits, "knowledge", reply.getRefKnowledgeIds(), knowledgeIds);
        return refs;
    }

    private static void addRefs(
            List<MessagePayload.RefPayload> refs,
            List<RecallHit> hits,
            String type,
            List<Long> requested,
            Set<Long> allowed) {
        if (requested == null) {
            return;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : requested) {
            if (id == null || !allowed.contains(id) || !seen.add(id)) {
                continue;
            }
            MessagePayload.RefPayload ref = new MessagePayload.RefPayload();
            ref.setType(type);
            ref.setId(id);
            ref.setTitle(titleOf(hits, type, id));
            refs.add(ref);
        }
    }

    private static String titleOf(List<RecallHit> hits, String type, Long id) {
        for (RecallHit hit : hits) {
            if (type.equals(hit.type()) && id.equals(hit.id())) {
                return hit.title();
            }
        }
        return "";
    }

    public record SanitizedTurn(String kind, String text, MessagePayload payload) {
    }
}
