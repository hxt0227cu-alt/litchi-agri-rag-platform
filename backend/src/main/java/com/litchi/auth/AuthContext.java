package com.litchi.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    public static final String USER_ATTRIBUTE = "litchi.auth.user";
    public static final String TOKEN_ATTRIBUTE = "litchi.auth.token";

    private AuthContext() {
    }

    public static AuthenticatedUser getCurrentUser(HttpServletRequest request) {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        return user instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : null;
    }

    public static AuthenticatedUser requireCurrentUser(HttpServletRequest request) {
        AuthenticatedUser user = getCurrentUser(request);
        if (user == null) {
            throw new IllegalStateException("当前请求未登录。");
        }
        return user;
    }

    public static String getCurrentToken(HttpServletRequest request) {
        Object token = request.getAttribute(TOKEN_ATTRIBUTE);
        return token instanceof String value ? value : null;
    }
}
