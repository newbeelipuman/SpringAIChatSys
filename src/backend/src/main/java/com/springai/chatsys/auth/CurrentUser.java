package com.springai.chatsys.auth;

public record CurrentUser(
        String userKey,
        String username,
        String displayName,
        String role,
        String source,
        boolean authenticated
) {
    public static CurrentUser authenticated(String userKey, String username, String role) {
        return new CurrentUser(userKey, username, username, AuthRole.from(role).name(), "auth-token", true);
    }

    public static CurrentUser demo(String userKey, String source) {
        return new CurrentUser(userKey, userKey, userKey, AuthRole.USER.name(), source, false);
    }
}
