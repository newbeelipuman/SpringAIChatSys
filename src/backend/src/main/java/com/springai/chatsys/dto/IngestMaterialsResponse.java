package com.springai.chatsys.dto;

import java.util.List;

public record IngestMaterialsResponse(
        List<IngestMaterialsItemDTO> items,
        int successCount,
        int failCount,
        int totalChunks,
        String vectorStoreMode,
        long elapsedMs
) {
}

