package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.RefreshTokenRequest;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class SecurityResource {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public SecurityResource(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/private/test")
    public String testProtectedEndpoint() {
        return "This is a private endpoint. You are authenticated!";
    }

    /**
     * Limited per client address and per account, so guessing passwords is slow even from many addresses.
     */
    @PostMapping("/api/auth/login")
    public TokenResponseDto login(@Valid @RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        rateLimiter.check(RateLimiter.LOGIN_IP, request.getRemoteAddr());
        rateLimiter.check(RateLimiter.LOGIN_EMAIL, loginRequest.username().trim().toLowerCase(Locale.ROOT));
        return authService.login(loginRequest);
    }

    /**
     * New access and refresh token for a valid refresh token; the old refresh token becomes invalid (rotation).
     * Works without access token, so the client can refresh after the access token expired.
     */
    @PostMapping("/api/auth/refresh")
    public TokenResponseDto refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Revokes the refresh token (and its family); the access token stays valid until it expires. */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }
}
