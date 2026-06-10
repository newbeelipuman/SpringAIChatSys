package com.springai.chatsys.dto;

import java.util.List;

public record MysqlTableStatusDTO(
        String tableName,
        boolean required,
        boolean present,
        List<String> missingColumns
) {
}
