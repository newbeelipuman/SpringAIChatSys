package com.springai.chatsys.dto;

public record AuthUserDTO(
        String userKey,
        String username,
        String displayName,
        String role,
        String source,
        boolean authenticated
) {
}
