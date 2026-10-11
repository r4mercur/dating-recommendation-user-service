package com.bjarne.datingrecommendationsuserservice.presence;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;

/**
 * Online status from the last activity in the database, so it works across instances: every authenticated request
 * and every open WebSocket session (refreshed once a minute) updates users.last_active_at, at most once per
 * {@code app.presence.write-interval}. A user counts as online while the last activity is within
 * {@code app.presence.online-threshold}. Users who hide their status are shown as unknown (null).
 */
@Service
public class PresenceService {

    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry;
    private final Duration onlineThreshold;
    private final Cache<String, Boolean> recentlyWritten;

    public PresenceService(UserRepository userRepository,
                           SimpUserRegistry simpUserRegistry,
                           @Value("${app.presence.online-threshold:PT2M}") Duration onlineThreshold,
                           @Value("${app.presence.write-interval:PT1M}") Duration writeInterval) {
        this.userRepository = userRepository;
        this.simpUserRegistry = simpUserRegistry;
        this.onlineThreshold = onlineThreshold;
        this.recentlyWritten = Caffeine.newBuilder().expireAfterWrite(writeInterval).maximumSize(100_000).build();
    }

    /** Records activity; writes to the database only if the last write of this instance is older than the interval. */
    public void touch(String referenceId) {
        if (recentlyWritten.asMap().putIfAbsent(referenceId, Boolean.TRUE) == null) {
            userRepository.touchLastActive(referenceId, Instant.now());
        }
    }

    /**
     * True if active within the threshold, false if not, null if the user hides the status.
     */
    public Boolean isOnline(User user) {
        if (!user.isShowOnlineStatus()) {
            return null;
        }
        return user.getLastActiveAt() != null && user.getLastActiveAt().isAfter(Instant.now().minus(onlineThreshold));
    }

    /** Users with an open WebSocket session on this instance stay online even without API requests. */
    @Scheduled(fixedDelayString = "${app.presence.write-interval:PT1M}")
    public void refreshConnectedUsers() {
        for (SimpUser user : simpUserRegistry.getUsers()) {
            touch(user.getName());
        }
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        record(event.getUser());
    }

    /** The disconnect is the last activity, so the status turns offline after the threshold. */
    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        record(event.getUser());
    }

    private void record(Principal principal) {
        if (principal != null) {
            recentlyWritten.invalidate(principal.getName());
            touch(principal.getName());
        }
    }
}
