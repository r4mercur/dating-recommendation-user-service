package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param action DISMISS: nothing to do; BLOCK_USER: block the reported user and close all their open reports
 */
public record ResolveReportRequest(@NotNull Action action, @Size(max = 1000) String note) {

    public enum Action {
        DISMISS,
        BLOCK_USER
    }
}
