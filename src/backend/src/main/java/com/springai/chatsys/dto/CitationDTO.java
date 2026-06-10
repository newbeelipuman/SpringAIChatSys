package com.springai.chatsys.dto;

public record CitationDTO(
        String chunkId,
        String docId,
        String documentName,
        String source,
        double score
) {
}
