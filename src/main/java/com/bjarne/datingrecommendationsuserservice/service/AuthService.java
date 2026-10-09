package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration tokenTtl;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtEncoder jwtEncoder,
                       @Value("${app.security.jwt.issuer}") String issuer,
                       @Value("${app.security.jwt.token-ttl:PT1H}") Duration tokenTtl) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.tokenTtl = tokenTtl;
    }

    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        String scope = (loginRequestDto.scope() == null || loginRequestDto.scope().isBlank()) ? "profile" : loginRequestDto.scope();
        String audience = (loginRequestDto.audience() == null || loginRequestDto.audience().isBlank()) ? "general" : loginRequestDto.audience();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDto.username(), loginRequestDto.password())
            );

            Instant now = Instant.now();
            Instant expiresAt = now.plus(tokenTtl);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(authentication.getName())
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .claim("scope", scope)
                    .claim("aud", List.of(audience))
                    .build();

            JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
            String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
            return new TokenResponseDto(token, "Bearer", tokenTtl.toSeconds(), scope);
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials", ex);
        }
    }
}
