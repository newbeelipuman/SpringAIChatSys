package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record HistoryMergeRequest(
        @NotEmpty List<String> questionIds,
        String docId,
        String documentName,
        String source
) {
}
