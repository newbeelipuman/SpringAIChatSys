package com.springai.chatsys.dto;

import java.util.List;

public record DemoMaterialDTO(
        String filename,
        String docId,
        String documentName,
        String displayName,
        String category,
        String description,
        List<String> sampleQuestions,
        String source,
        long bytes
) {
}
