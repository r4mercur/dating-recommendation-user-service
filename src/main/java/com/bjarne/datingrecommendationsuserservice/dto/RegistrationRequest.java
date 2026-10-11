package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Interests and hobbies are tag slugs from GET /api/tags. Preferences are optional and default to
 * "all genders, 18-99, 50 km".
 */
public record RegistrationRequest(@NotBlank @Size(max = 255) String name,
                                  @NotBlank @Email @Size(max = 255) String email,
                                  @NotBlank @Size(min = 8, max = 128) String password,
                                  @Size(max = 1000) String bio,
                                  @NotNull @Past LocalDate birthDate,
                                  @Size(max = 20) List<@NotBlank String> interests,
                                  @Size(max = 20) List<@NotBlank String> hobbies,
                                  @NotNull @Valid AddressDto address,
                                  @NotNull Gender gender,
                                  @Valid SearchPreferencesDto preferences) {
}
