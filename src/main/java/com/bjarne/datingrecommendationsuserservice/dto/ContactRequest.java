package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.ContactStatus;
import jakarta.validation.constraints.NotNull;

public record ContactRequest(@NotNull String userReferenceId,
                             @NotNull String contactReferenceId,
                             ContactStatus status) {

}
