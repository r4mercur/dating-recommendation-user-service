package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;

import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * The logged-in user's own profile, including private fields like email, full address and preferences.
 */
public record MyProfileResponse(String referenceId,
                                String name,
                                String email,
                                String bio,
                                LocalDate birthDate,
                                Integer age,
                                Gender gender,
                                List<String> interests,
                                List<String> hobbies,
                                AddressDto address,
                                Double latitude,
                                Double longitude,
                                SearchPreferencesDto preferences,
                                UserStatus status,
                                String photo,
                                List<SongResponse> songs,
                                List<GameResult> games,
                                boolean showOnlineStatus) {
}
