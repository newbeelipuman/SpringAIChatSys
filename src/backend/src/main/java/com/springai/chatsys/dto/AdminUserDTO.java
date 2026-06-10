package com.springai.chatsys.dto;

public record AdminUserDTO(
        String userKey,
        String username,
        String role,
        boolean enabled,
        String createdAt,
        String updatedAt
) {
}
