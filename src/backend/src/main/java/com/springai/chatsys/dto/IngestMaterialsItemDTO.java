package com.springai.chatsys.dto;

public record IngestMaterialsItemDTO(
        String filename,
        IngestResponse ingest,
        String error
) {
}

