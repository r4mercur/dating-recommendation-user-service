package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.moderation.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param comment required for OTHER
 * @param block   also block the user right away (recommended in the UI); optional, default false
 */
public record ReportRequest(@NotBlank String referenceId,
                            @NotNull ReportReason reason,
                            @Size(max = 1000) String comment,
                            Boolean block) {

    public boolean shouldBlock() {
        return Boolean.TRUE.equals(block);
    }
}
