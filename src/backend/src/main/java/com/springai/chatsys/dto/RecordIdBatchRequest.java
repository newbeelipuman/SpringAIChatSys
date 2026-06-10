package com.springai.chatsys.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecordIdBatchRequest(
        @NotEmpty(message = "请选择要删除的记录")
        List<String> ids
) {
}
