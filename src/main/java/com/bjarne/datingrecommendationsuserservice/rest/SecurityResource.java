package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import com.bjarne.datingrecommendationsuserservice.service.AuthorizationBridgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SecurityResource {

    private final AuthorizationBridgeService authorizationBridgeService;

    public SecurityResource(AuthorizationBridgeService authorizationBridgeService) {
        this.authorizationBridgeService = authorizationBridgeService;
    }

    @GetMapping("/api/private/test")
    public String testProtectedEndpoint() {
        return "This is a private endpoint. You are authenticated!";
    }

    @PostMapping("/api/auth/login")
    public TokenResponseDto login(@RequestBody LoginRequestDto loginRequest) {
        if (loginRequest.username() == null || loginRequest.username().isBlank() ||
                loginRequest.password() == null || loginRequest.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
        }
        return authorizationBridgeService.login(loginRequest);
    }
}
