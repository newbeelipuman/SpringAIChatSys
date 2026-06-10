package com.springai.chatsys.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthAccessService {

    public boolean isAdmin(CurrentUser user) {
        return user != null && AuthRole.from(user.role()) == AuthRole.ADMIN;
    }

    public boolean isAuthenticated(CurrentUser user) {
        return user != null && user.authenticated();
    }

    public void requireAdmin(CurrentUser user) {
        if (!isAdmin(user)) {
            throw new AuthAccessDeniedException("Admin role required.");
        }
    }

    public void requirePermission(CurrentUser user, AuthPermission permission) {
        if (user == null || !AuthRole.from(user.role()).hasPermission(permission)) {
            throw new AuthAccessDeniedException("Permission required: " + permission.name());
        }
    }
}
