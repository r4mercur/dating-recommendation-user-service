package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.moderation.Report;
import com.bjarne.datingrecommendationsuserservice.moderation.ReportReason;
import com.bjarne.datingrecommendationsuserservice.moderation.ReportStatus;

import java.time.Instant;

/**
 * What the reporter sees; the outcome of the moderation is not shared.
 */
public record ReportResponse(String referenceId, String reportedReferenceId, ReportReason reason, ReportStatus status,
                             Instant createdAt, boolean blocked) {

    public static ReportResponse from(Report report, boolean blocked) {
        return new ReportResponse(report.getReferenceId(), report.getReported().getReferenceId(), report.getReason(),
                report.getStatus(), report.getCreatedAt(), blocked);
    }
}
