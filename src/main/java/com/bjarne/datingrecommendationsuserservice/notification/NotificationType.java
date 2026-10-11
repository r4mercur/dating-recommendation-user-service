package com.bjarne.datingrecommendationsuserservice.notification;

/**
 * Stored as string, a new type needs no migration. New chat messages have no notification of their own:
 * the unread counters of the conversations cover them.
 */
public enum NotificationType {
    /** Someone reacted positively (LIKE, SUPER_LIKE, COMPLIMENT); not sent if it created a match. */
    NEW_LIKE,
    /** Both reacted positively; the conversation is open. */
    NEW_MATCH
}
