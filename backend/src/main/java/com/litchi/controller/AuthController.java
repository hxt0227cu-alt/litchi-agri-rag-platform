package com.litchi.controller;

import com.litchi.auth.AuthContext;
import com.litchi.auth.AuthRequired;
import com.litchi.auth.AuthService;
import com.litchi.dto.AuthResponse;
import com.litchi.dto.AuthUserView;
import com.litchi.dto.LoginRequest;
import com.litchi.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @AuthRequired
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        authService.logout(AuthContext.getCurrentToken(request));
        return ResponseEntity.ok(Map.of("success", true, "message", "已退出登录。"));
    }

    @AuthRequired
    @GetMapping("/me")
    public ResponseEntity<AuthUserView> me(HttpServletRequest request) {
        String token = AuthContext.getCurrentToken(request);
        return ResponseEntity.ok(authService.me(token));
    }
}
