package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import com.bjarne.datingrecommendationsuserservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityResource {

    private final AuthService authService;

    public SecurityResource(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/private/test")
    public String testProtectedEndpoint() {
        return "This is a private endpoint. You are authenticated!";
    }

    @PostMapping("/api/auth/login")
    public TokenResponseDto login(@Valid @RequestBody LoginRequestDto loginRequest) {
        return authService.login(loginRequest);
    }
}
