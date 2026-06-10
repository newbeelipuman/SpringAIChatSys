package com.springai.chatsys.service;

public final class DemoUserContext {

    public static final String DEFAULT_USER_ID = "demo-user";

    private DemoUserContext() {
    }

    public static String resolve(String userId) {
        if (userId == null || userId.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return sanitize(userId.trim());
    }

    private static String sanitize(String value) {
        String result = value.replaceAll("[^a-zA-Z0-9_.@-]", "-");
        if (result.length() > 64) {
            return result.substring(0, 64);
        }
        return result.isBlank() ? DEFAULT_USER_ID : result;
    }
}
