package com.springai.chatsys.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> allItems, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int total = allItems.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        return new PageResponse<>(allItems.subList(fromIndex, toIndex), total, safePage, safeSize, totalPages);
    }
}
