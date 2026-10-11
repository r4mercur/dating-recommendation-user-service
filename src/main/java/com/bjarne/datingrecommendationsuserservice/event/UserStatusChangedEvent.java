package com.bjarne.datingrecommendationsuserservice.event;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;

import java.time.Instant;

public record UserStatusChangedEvent(
        String referenceId,
        UserStatus oldStatus,
        UserStatus newStatus,
        Instant occurredAt
) {
    public static UserStatusChangedEvent of(String referenceId, UserStatus oldStatus, UserStatus newStatus) {
        return new UserStatusChangedEvent(referenceId, oldStatus, newStatus, Instant.now());
    }
}
