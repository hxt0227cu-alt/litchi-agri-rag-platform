package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.ChatRequest;
import com.litchi.dto.ChatHistoryItem;
import com.litchi.dto.ChatResponse;
import com.litchi.dto.ChatSessionItem;
import com.litchi.dto.PageResponse;
import com.litchi.service.ChatService;
import com.litchi.service.ChatHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    @AuthRequired
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        ChatResponse response = chatService.processChat(request);
        chatHistoryService.save(user.id(), request.getSessionId(), request.getQuestion(), response);
        return ResponseEntity.ok(response);
    }

    @AuthRequired
    @GetMapping("/history")
    public ResponseEntity<PageResponse<ChatHistoryItem>> history(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(chatHistoryService.getHistory(user.id(), sessionId, page, size));
    }

    @AuthRequired
    @GetMapping("/sessions")
    public ResponseEntity<PageResponse<ChatSessionItem>> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(chatHistoryService.getSessions(user.id(), page, size));
    }
}
