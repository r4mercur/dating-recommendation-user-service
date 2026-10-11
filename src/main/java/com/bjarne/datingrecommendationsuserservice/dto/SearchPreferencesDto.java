package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.SearchPreferences;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.Set;

public record SearchPreferencesDto(@NotEmpty Set<Gender> seekingGenders,
                                   @NotNull @Min(18) @Max(99) Integer minAge,
                                   @NotNull @Min(18) @Max(99) Integer maxAge,
                                   @NotNull @Min(1) @Max(500) Integer maxDistanceKm) {

    public static SearchPreferencesDto from(SearchPreferences preferences) {
        return new SearchPreferencesDto(
                Set.copyOf(preferences.getSeekingGenders()),
                preferences.getMinAge(),
                preferences.getMaxAge(),
                preferences.getMaxDistanceKm()
        );
    }

    @JsonIgnore
    @AssertTrue(message = "minAge must not be greater than maxAge")
    public boolean isAgeRangeValid() {
        return minAge == null || maxAge == null || minAge <= maxAge;
    }

    public void applyTo(SearchPreferences preferences) {
        preferences.setSeekingGenders(new HashSet<>(seekingGenders));
        preferences.setMinAge(minAge);
        preferences.setMaxAge(maxAge);
        preferences.setMaxDistanceKm(maxDistanceKm);
    }
}
