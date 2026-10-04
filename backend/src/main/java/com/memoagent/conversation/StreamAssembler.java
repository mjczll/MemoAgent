package com.memoagent.conversation;

/**
 * 把模型输出拆成给用户看的正文，以及末尾的结构化标记。
 */
public final class StreamAssembler {

    public static final String MARKER = "<<<META>>>";

    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder visible = new StringBuilder();
    private final StringBuilder meta = new StringBuilder();
    private boolean inMeta;

    public String push(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (inMeta) {
            meta.append(token);
            return "";
        }
        pending.append(token);
        int marker = indexOfMarker(pending);
        if (marker >= 0) {
            String show = pending.substring(0, marker);
            visible.append(show);
            inMeta = true;
            meta.append(pending.substring(marker + MARKER.length()));
            pending.setLength(0);
            return show;
        }
        int keep = MARKER.length() - 1;
        if (pending.length() <= keep) {
            return "";
        }
        String show = pending.substring(0, pending.length() - keep);
        visible.append(show);
        pending.delete(0, show.length());
        return show;
    }

    public String finish() {
        if (inMeta) {
            return "";
        }
        String show = pending.toString();
        visible.append(show);
        pending.setLength(0);
        return show;
    }

    public String visibleText() {
        return visible.toString().strip();
    }

    public String metaText() {
        return meta.toString().strip();
    }

    private int indexOfMarker(StringBuilder buffer) {
        return buffer.indexOf(MARKER);
    }
}
