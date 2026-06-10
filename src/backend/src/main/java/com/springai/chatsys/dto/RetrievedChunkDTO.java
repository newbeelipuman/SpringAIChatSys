package com.springai.chatsys.dto;

public record RetrievedChunkDTO(
        String chunkId,
        String docId,
        String documentName,
        String content,
        String source,
        double score
) {
}
