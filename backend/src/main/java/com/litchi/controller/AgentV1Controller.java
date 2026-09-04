package com.litchi.controller;

import com.litchi.agent.AgentService;
import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.AgentApprovalRequest;
import com.litchi.dto.AgentRunRequest;
import com.litchi.dto.AgentRunResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/v1/agent-runs")
@RequiredArgsConstructor
@AuthRequired
public class AgentV1Controller {
    private final AgentService agentService;

    @PostMapping
    public ResponseEntity<AgentRunResponse> start(
            @Valid @RequestBody AgentRunRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(agentService.start(request, user));
    }

    @GetMapping
    public ResponseEntity<List<AgentRunResponse>> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String status,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.ok(agentService.list(user, limit, status));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<AgentRunResponse> get(@PathVariable String runId, HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.ok(agentService.get(runId, user));
    }

    @GetMapping("/{runId}/events")
    public SseEmitter events(@PathVariable String runId, HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        agentService.get(runId, user);
        return agentService.events(runId);
    }

    @PostMapping("/{runId}/confirm")
    public ResponseEntity<AgentRunResponse> confirm(
            @PathVariable String runId,
            @Valid @RequestBody AgentApprovalRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.ok(agentService.confirm(runId, request.getDecision(), user));
    }

    @PostMapping("/{runId}/cancel")
    public ResponseEntity<AgentRunResponse> cancel(@PathVariable String runId, HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.ok(agentService.cancel(runId, user));
    }
}
