package com.bjarne.datingrecommendationsuserservice.interaction;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * @param dailyLimits reactions per type and day; types without entry are unlimited (interaction.daily-limits.SUPER_LIKE=3)
 * @param timeZone    zone that defines when a day starts for the limits
 */
@ConfigurationProperties("interaction")
public record InteractionProperties(Map<ReactionType, Integer> dailyLimits, ZoneId timeZone) {

    public InteractionProperties {
        dailyLimits = dailyLimits == null ? new EnumMap<>(ReactionType.class) : new EnumMap<>(dailyLimits);
        timeZone = timeZone == null ? ZoneId.of("Europe/Berlin") : timeZone;
    }

    public Integer dailyLimit(ReactionType type) {
        return dailyLimits.get(type);
    }
}
