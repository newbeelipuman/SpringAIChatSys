package com.springai.chatsys.domain;

import java.time.Instant;
import java.util.List;

public record DocumentChunk(
        String chunkId,
        String docId,
        String documentName,
        String content,
        String source,
        List<Double> embedding,
        Instant createdAt
) {
}
