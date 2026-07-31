package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.FeedbackRecordDto;
import com.litchi.dto.FeedbackStatsResponse;
import com.litchi.dto.SubmitFeedbackRequest;
import com.litchi.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feedbacks")
@RequiredArgsConstructor
@AuthRequired
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<FeedbackRecordDto> submit(
            HttpServletRequest request,
            @Valid @RequestBody SubmitFeedbackRequest feedbackRequest
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(request);
        return ResponseEntity.ok(feedbackService.submit(user, feedbackRequest));
    }

    @GetMapping("/stats")
    @RoleAllowed("technician")
    public ResponseEntity<FeedbackStatsResponse> stats() {
        return ResponseEntity.ok(feedbackService.getStats());
    }
}
