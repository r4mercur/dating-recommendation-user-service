package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.auth.LoginRequestDto;
import com.bjarne.datingrecommendationsuserservice.dto.auth.TokenResponseDto;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.security.RefreshTokenService;
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

    private static final String DEFAULT_SCOPE = "profile";
    private static final String DEFAULT_AUDIENCE = "general";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenService refreshTokenService;
    private final String issuer;
    private final Duration tokenTtl;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtEncoder jwtEncoder,
                       RefreshTokenService refreshTokenService,
                       @Value("${app.security.jwt.issuer}") String issuer,
                       @Value("${app.security.jwt.token-ttl:PT1H}") Duration tokenTtl) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenService = refreshTokenService;
        this.issuer = issuer;
        this.tokenTtl = tokenTtl;
    }

    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        String scope = (loginRequestDto.scope() == null || loginRequestDto.scope().isBlank()) ? DEFAULT_SCOPE : loginRequestDto.scope();
        String audience = (loginRequestDto.audience() == null || loginRequestDto.audience().isBlank()) ? DEFAULT_AUDIENCE : loginRequestDto.audience();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDto.username(), loginRequestDto.password())
            );

            // the subject is the stable referenceId, so endpoints never have to trust user ids from the request
            User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                    .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

            return tokens(user, scope, audience, refreshTokenService.issue(user));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials", ex);
        }
    }

    /**
     * New access token plus a new refresh token (rotation). Role changes made since the last login take effect here.
     */
    public TokenResponseDto refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return tokens(rotation.user(), DEFAULT_SCOPE, DEFAULT_AUDIENCE, rotation.token());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenResponseDto tokens(User user, String scope, String audience, RefreshTokenService.IssuedToken refreshToken) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getReferenceId())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                .claim("scope", scope)
                // permissions come only from the database, never from the request (unlike the scope above)
                .claim("roles", List.of(user.getRole().name()))
                .claim("aud", List.of(audience))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
        long refreshExpiresIn = Duration.between(now, refreshToken.expiresAt()).toSeconds();
        return new TokenResponseDto(accessToken, "Bearer", tokenTtl.toSeconds(), scope, refreshToken.token(), refreshExpiresIn);
    }
}
