package com.bjarne.datingrecommendationsuserservice.interaction;

import java.util.Arrays;
import java.util.List;

/**
 * All reaction types with their behaviour. A new type is a new constant here (stored as string, no migration);
 * a daily limit is configured via interaction.daily-limits.&lt;TYPE&gt;.
 */
public enum ReactionType {
    LIKE(true, MessagePolicy.NONE),
    SUPER_LIKE(true, MessagePolicy.NONE),
    /** A like with a personal message. */
    COMPLIMENT(true, MessagePolicy.REQUIRED),
    PASS(false, MessagePolicy.NONE);

    /** Whether the reaction counts as interest: two positive reactions create a match. */
    private final boolean positive;
    private final MessagePolicy messagePolicy;

    ReactionType(boolean positive, MessagePolicy messagePolicy) {
        this.positive = positive;
        this.messagePolicy = messagePolicy;
    }

    public boolean isPositive() {
        return positive;
    }

    public MessagePolicy getMessagePolicy() {
        return messagePolicy;
    }

    public static List<ReactionType> positiveTypes() {
        return Arrays.stream(values()).filter(ReactionType::isPositive).toList();
    }

    public enum MessagePolicy {
        NONE,
        OPTIONAL,
        REQUIRED
    }
}
