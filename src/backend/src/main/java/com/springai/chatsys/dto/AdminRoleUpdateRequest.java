package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRoleUpdateRequest(
        @NotBlank String role
) {
}
