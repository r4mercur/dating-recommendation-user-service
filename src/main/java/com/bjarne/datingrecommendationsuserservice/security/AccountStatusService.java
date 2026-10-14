package com.bjarne.datingrecommendationsuserservice.security;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Whether an account may still use the API. A JWT stays valid until it expires, so without this check a blocked or
 * deleted account could keep chatting for up to an hour. The status is cached briefly and evicted on changes made
 * by this instance; other instances see a change after at most {@link #CACHE_TTL}.
 */
@Service
public class AccountStatusService {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Set<UserStatus> LOCKED_OUT = Set.of(UserStatus.BLOCKED, UserStatus.MARKED_FOR_DELETION);

    private final UserRepository userRepository;
    private final Cache<String, Optional<UserStatus>> statuses = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(100_000)
            .build();

    public AccountStatusService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * False for blocked accounts, accounts marked for deletion and accounts that no longer exist.
     */
    public boolean mayUseApi(String referenceId) {
        Optional<UserStatus> status = statuses.get(referenceId,
                id -> userRepository.findByReferenceId(id).map(User::getStatus));
        return status.isEmpty() || LOCKED_OUT.contains(status.get());
    }

    @EventListener
    public void on(UserStatusChangedEvent event) {
        statuses.invalidate(event.referenceId());
    }

    /** For deleted accounts, which publish no status event. */
    public void forget(String referenceId) {
        statuses.invalidate(referenceId);
    }
}
