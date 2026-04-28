package com.tlq.rectagent.context;

public final class SessionContextKeys {

    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";

    public static final String MESSAGE_COUNT = "__msg_count__";
    public static final String ESTIMATED_TOKENS = "__estimated_tokens__";
    public static final String COMPACT_VERSION = "__compact_version__";
    public static final String HAS_COMPACTED = "__has_compacted__";
    public static final String COMPACT_SUMMARY = "__compact_summary__";

    private SessionContextKeys() {
    }
}