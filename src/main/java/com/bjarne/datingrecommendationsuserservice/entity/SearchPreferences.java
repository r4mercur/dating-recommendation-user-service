package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Who a user wants to be matched with. Used as filter for recommendations (in both directions).
 */
@Embeddable
@Getter
@Setter
public class SearchPreferences {

    public static final int DEFAULT_MIN_AGE = 18;
    public static final int DEFAULT_MAX_AGE = 99;
    public static final int DEFAULT_MAX_DISTANCE_KM = 50;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_seeking_genders", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Set<Gender> seekingGenders = new HashSet<>();

    @Column(name = "min_age", nullable = false)
    private Integer minAge;

    @Column(name = "max_age", nullable = false)
    private Integer maxAge;

    @Column(name = "max_distance_km", nullable = false)
    private Integer maxDistanceKm;

    public static SearchPreferences defaults() {
        SearchPreferences preferences = new SearchPreferences();
        preferences.setSeekingGenders(new HashSet<>(EnumSet.allOf(Gender.class)));
        preferences.setMinAge(DEFAULT_MIN_AGE);
        preferences.setMaxAge(DEFAULT_MAX_AGE);
        preferences.setMaxDistanceKm(DEFAULT_MAX_DISTANCE_KM);
        return preferences;
    }
}
