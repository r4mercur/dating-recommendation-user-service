package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Partial update of the own profile: fields that are null stay unchanged.
 * A new address is located again, preferences have their own endpoint.
 */
public record UpdateProfileRequest(@Size(min = 1, max = 255) String name,
                                   @Size(max = 1000) String bio,
                                   @Past LocalDate birthDate,
                                   @Size(max = 20) List<@NotBlank String> interests,
                                   @Size(max = 20) List<@NotBlank String> hobbies,
                                   @Valid AddressDto address,
                                   Gender gender) {
}
