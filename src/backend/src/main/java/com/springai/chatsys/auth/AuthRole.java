package com.springai.chatsys.auth;

import java.util.Set;

public enum AuthRole {
    USER(Set.of(
            AuthPermission.VIEW_OWN_HISTORY,
            AuthPermission.CLEAR_OWN_HISTORY
    )),
    ADMIN(Set.of(
            AuthPermission.VIEW_OWN_HISTORY,
            AuthPermission.CLEAR_OWN_HISTORY,
            AuthPermission.VIEW_ADMIN_HISTORY,
            AuthPermission.MANAGE_USERS,
            AuthPermission.ASSIGN_ROLES,
            AuthPermission.RESET_USER_PASSWORD,
            AuthPermission.DELETE_USER_DATA
    ));

    private final Set<AuthPermission> permissions;

    AuthRole(Set<AuthPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<AuthPermission> permissions() {
        return permissions;
    }

    public boolean hasPermission(AuthPermission permission) {
        return permissions.contains(permission);
    }

    public static AuthRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        for (AuthRole role : values()) {
            if (role.name().equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        return USER;
    }

    public static AuthRole require(String value) {
        if (value != null && !value.isBlank()) {
            for (AuthRole role : values()) {
                if (role.name().equalsIgnoreCase(value.trim())) {
                    return role;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported role.");
    }
}
