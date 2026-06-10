package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
