package com.springai.chatsys.dto;

public record AskResponse(
        String answer,
        String mode,
        long elapsedMs
) {
}
