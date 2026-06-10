package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 128) String newPassword
) {
}
