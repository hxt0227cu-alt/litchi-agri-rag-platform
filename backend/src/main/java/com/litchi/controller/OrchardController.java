package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.OrchardRequest;
import com.litchi.dto.OrchardResponse;
import com.litchi.service.OrchardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orchards")
@RequiredArgsConstructor
@AuthRequired
@RoleAllowed({"farmer", "technician"})
public class OrchardController {
    private final OrchardService orchardService;

    @GetMapping
    public ResponseEntity<List<OrchardResponse>> list(HttpServletRequest servletRequest) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.ok(orchardService.list(user));
    }

    @PostMapping
    public ResponseEntity<OrchardResponse> create(
            @Valid @RequestBody OrchardRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser user = AuthContext.requireCurrentUser(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(orchardService.create(user, request));
    }
}
