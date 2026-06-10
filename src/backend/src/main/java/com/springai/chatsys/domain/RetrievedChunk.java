package com.springai.chatsys.domain;

public record RetrievedChunk(
        String chunkId,
        String docId,
        String documentName,
        String content,
        String source,
        double score
) {
}
