package com.springai.chatsys.dto;

import java.util.Map;

public record HealthResponse(
        String status,
        Map<String, Object> details
) {
}
