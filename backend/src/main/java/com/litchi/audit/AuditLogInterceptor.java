package com.litchi.audit;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final String STARTED_AT_ATTRIBUTE = "litchi.audit.startedAt";
    private static final Set<String> MUTATING_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(STARTED_AT_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !shouldAudit(request)) {
            return;
        }

        AuthenticatedUser user = AuthContext.getCurrentUser(request);
        auditLog.info(
                "event=request_audit handler={}#{} method={} path={} status={} outcome={} durationMs={} clientIp={} userId={} username={} role={}",
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName(),
                normalizeMethod(request.getMethod()),
                sanitizePath(request.getRequestURI()),
                response.getStatus(),
                resolveOutcome(response.getStatus(), exception),
                resolveDurationMs(request),
                resolveClientIp(request),
                valueOrDash(user == null ? null : user.id()),
                valueOrDash(user == null ? null : user.username()),
                valueOrDash(user == null ? null : user.role())
        );
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = normalizeMethod(request.getMethod());
        return MUTATING_METHODS.contains(method) && !"/error".equals(request.getRequestURI());
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "UNKNOWN";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private String resolveOutcome(int status, Exception exception) {
        if (exception != null || status >= 500) {
            return "server_error";
        }
        if (status >= 400) {
            return "client_error";
        }
        return "success";
    }

    private long resolveDurationMs(HttpServletRequest request) {
        Object startedAt = request.getAttribute(STARTED_AT_ATTRIBUTE);
        if (startedAt instanceof Long value) {
            return Math.max(0L, System.currentTimeMillis() - value);
        }
        return -1L;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return valueOrDash(remoteAddr);
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
