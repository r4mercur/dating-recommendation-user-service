package com.bjarne.datingrecommendationsuserservice.security;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.InvalidRefreshTokenException;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Long-lived refresh tokens with rotation: every refresh returns a new token and invalidates the old one.
 * Using an already rotated token again means it was copied, so the whole family (all tokens of that login) is revoked.
 * Logout, a password change, blocking and deleting the account revoke the tokens as well.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final Set<UserStatus> LOCKED_OUT = Set.of(UserStatus.BLOCKED, UserStatus.MARKED_FOR_DELETION);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AccountStatusService accountStatusService;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               AccountStatusService accountStatusService,
                               @Value("${app.security.refresh-token-ttl:P30D}") Duration ttl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.accountStatusService = accountStatusService;
        this.ttl = ttl;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record Rotation(User user, IssuedToken token) {
    }

    /** Starts a new token family, e.g. at login. */
    @Transactional
    public IssuedToken issue(User user) {
        return create(user, UUID.randomUUID().toString());
    }

    /**
     * Exchanges a valid refresh token for a new one of the same family. The revocation of a reused family
     * is committed although the request fails.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (token.getUsedAt() != null && token.getRevokedAt() == null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            LOGGER.warn("Refresh token of user {} was used twice, revoked its family", token.getUser().getReferenceId());
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsable(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = token.getUser();
        if (accountStatusService.mayUseApi(user.getReferenceId())) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        token.markUsed(now);
        return new Rotation(user, create(user, token.getFamilyId()));
    }

    /** Logout: revokes the token and all tokens of its family. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /** Logs the user out everywhere once the current access tokens expire, e.g. after a password change. */
    @Transactional
    public void revokeAll(String referenceId) {
        userRepository.findByReferenceId(referenceId)
                .ifPresent(user -> refreshTokenRepository.revokeAllOfUser(user.getId(), Instant.now()));
    }

    @EventListener
    public void on(UserStatusChangedEvent event) {
        if (LOCKED_OUT.contains(event.newStatus())) {
            revokeAll(event.referenceId());
        }
    }

    @Scheduled(cron = "${app.security.refresh-token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            LOGGER.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    private IssuedToken create(User user, String familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttl);
        refreshTokenRepository.save(new RefreshToken(user, hash(raw), familyId, expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    /** A plain SHA-256 is enough: the tokens are 256 bit random values, not passwords. */
    static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
