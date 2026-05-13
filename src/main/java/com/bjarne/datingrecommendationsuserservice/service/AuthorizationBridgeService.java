package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationBridgeService {

    private final RestClient stsRestClient;

    public AuthorizationBridgeService(RestClient stsRestClient) {
        this.stsRestClient = stsRestClient;
    }

    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        String scope = (loginRequestDto.scope() == null || loginRequestDto.scope().isBlank()) ? "profile" : loginRequestDto.scope();
        String audience = (loginRequestDto.audience() == null || loginRequestDto.audience().isBlank()) ? "general" : loginRequestDto.audience();

        LoginRequestDto stsRequest = new LoginRequestDto(
            loginRequestDto.username(),
            loginRequestDto.password(),
            scope,
            audience
        );

        try {
            return stsRestClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stsRequest)
                    .retrieve()
                    .body(TokenResponseDto.class);

        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service error");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auth service unavailable");
        }
    }
}
