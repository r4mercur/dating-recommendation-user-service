package com.bjarne.datingrecommendationsuserservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Privacy settings of the own account.
 *
 * @param showOnlineStatus whether others see if the user is online (otherwise they get {@code online: null})
 */
public record PrivacyRequest(@NotNull Boolean showOnlineStatus) {
}
