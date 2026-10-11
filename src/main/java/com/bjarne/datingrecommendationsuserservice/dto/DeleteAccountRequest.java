package com.bjarne.datingrecommendationsuserservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Deleting the account must be confirmed with the password.
 */
public record DeleteAccountRequest(@NotBlank String password) {
}
