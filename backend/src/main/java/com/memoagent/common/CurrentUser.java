package com.memoagent.common;

public final class CurrentUser {

    public static final long DEFAULT_ID = 1L;

    private CurrentUser() {
    }

    public static long id() {
        return DEFAULT_ID;
    }
}
