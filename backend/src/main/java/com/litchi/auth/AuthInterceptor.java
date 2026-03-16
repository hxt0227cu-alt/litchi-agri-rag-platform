package com.litchi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveBearerToken(request);
        AuthenticatedUser currentUser = null;
        if (token != null) {
            currentUser = authService.resolveUser(token);
            if (currentUser != null) {
                request.setAttribute(AuthContext.USER_ATTRIBUTE, currentUser);
                request.setAttribute(AuthContext.TOKEN_ATTRIBUTE, token);
            }
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean authRequired = handlerMethod.hasMethodAnnotation(AuthRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AuthRequired.class);
        RoleAllowed roleAllowed = findRoleAllowed(handlerMethod);

        if ((authRequired || roleAllowed != null) && currentUser == null) {
            return writeJsonResponse(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("message", "Authentication is required.")
            );
        }

        if (roleAllowed != null && !isRoleAllowed(currentUser.role(), roleAllowed.value())) {
            return writeJsonResponse(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    Map.of(
                            "message", "Your role is not allowed to access this resource.",
                            "role", currentUser.role()
                    )
            );
        }

        return true;
    }

    private RoleAllowed findRoleAllowed(HandlerMethod handlerMethod) {
        RoleAllowed annotation = handlerMethod.getMethodAnnotation(RoleAllowed.class);
        if (annotation != null) {
            return annotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RoleAllowed.class);
    }

    private boolean isRoleAllowed(String currentRole, String[] allowedRoles) {
        if (currentRole == null || currentRole.isBlank() || allowedRoles == null || allowedRoles.length == 0) {
            return false;
        }

        Set<String> allowed = Arrays.stream(allowedRoles)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return allowed.contains(currentRole.trim().toLowerCase());
    }

    private boolean writeJsonResponse(HttpServletResponse response, int status, Map<String, Object> payload) throws Exception {
        byte[] body = objectMapper.writeValueAsString(payload)
                .getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(body);
        return false;
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String value = authorization.substring(7).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
