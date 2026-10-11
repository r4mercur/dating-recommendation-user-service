package com.bjarne.datingrecommendationsuserservice.moderation;

public enum ReportStatus {
    /** Waiting for a moderator. */
    OPEN,
    /** A moderator acted on it, e.g. blocked the reported user. */
    RESOLVED,
    /** A moderator found nothing to do. */
    DISMISSED
}
