package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
