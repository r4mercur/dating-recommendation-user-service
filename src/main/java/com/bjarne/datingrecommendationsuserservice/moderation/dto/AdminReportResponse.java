package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.moderation.ReportReason;
import com.bjarne.datingrecommendationsuserservice.moderation.ReportStatus;

import java.time.Instant;

/**
 * @param reporterReferenceId null if the reporter deleted their account
 */
public record AdminReportResponse(String referenceId,
                                  ReportReason reason,
                                  String comment,
                                  ReportStatus status,
                                  Instant createdAt,
                                  String reporterReferenceId,
                                  ModeratedUser reported,
                                  Instant resolvedAt,
                                  String resolvedByReferenceId,
                                  String resolutionNote) {
}
