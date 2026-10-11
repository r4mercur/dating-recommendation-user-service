package com.bjarne.datingrecommendationsuserservice.moderation;

/**
 * Why a user was reported. Stored as string, a new reason needs no migration. OTHER requires a comment.
 */
public enum ReportReason {
    SPAM,
    FAKE_PROFILE,
    HARASSMENT,
    INAPPROPRIATE_CONTENT,
    UNDERAGE,
    SCAM,
    OTHER
}
