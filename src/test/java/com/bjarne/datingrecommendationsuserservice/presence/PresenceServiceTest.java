package com.bjarne.datingrecommendationsuserservice.presence;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PresenceServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SimpUserRegistry simpUserRegistry = mock(SimpUserRegistry.class);
    private final PresenceService presenceService =
            new PresenceService(userRepository, simpUserRegistry, Duration.ofMinutes(2), Duration.ofMinutes(1));

    @Test
    void activityIsWrittenAtMostOncePerInterval() {
        presenceService.touch("anna");
        presenceService.touch("anna");
        presenceService.touch("ben");

        verify(userRepository, times(1)).touchLastActive(eq("anna"), any());
        verify(userRepository, times(1)).touchLastActive(eq("ben"), any());
    }

    @Test
    void openWebSocketSessionsKeepUsersOnline() {
        SimpUser connected = mock(SimpUser.class);
        when(connected.getName()).thenReturn("carl");
        when(simpUserRegistry.getUsers()).thenReturn(Set.of(connected));

        presenceService.refreshConnectedUsers();

        verify(userRepository).touchLastActive(eq("carl"), any());
    }

    @Test
    void onlineMeansActiveWithinTheThresholdUnlessHidden() {
        assertThat(presenceService.isOnline(user(Instant.now().minusSeconds(30), true))).isTrue();
        assertThat(presenceService.isOnline(user(Instant.now().minusSeconds(600), true))).isFalse();
        assertThat(presenceService.isOnline(user(null, true))).isFalse();
        assertThat(presenceService.isOnline(user(Instant.now(), false))).isNull();
    }

    private static User user(Instant lastActiveAt, boolean showOnlineStatus) {
        User user = new User();
        user.setLastActiveAt(lastActiveAt);
        user.setShowOnlineStatus(showOnlineStatus);
        return user;
    }
}
