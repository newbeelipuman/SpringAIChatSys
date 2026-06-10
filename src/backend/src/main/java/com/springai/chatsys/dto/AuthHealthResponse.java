package com.springai.chatsys.dto;

public record AuthHealthResponse(
        boolean authEnabled,
        String authMode,
        String currentIdentitySource,
        String currentRole,
        boolean mysqlUserTableAvailable,
        boolean authSessionTableAvailable,
        boolean permissionRelationAvailable,
        boolean adminUserAvailable,
        boolean adminEndpointsProtected,
        String lastError
) {
}
