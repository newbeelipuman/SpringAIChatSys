package com.springai.chatsys.dto;

public record AuthResponse(
        String token,
        String tokenType,
        AuthUserDTO user
) {
}
