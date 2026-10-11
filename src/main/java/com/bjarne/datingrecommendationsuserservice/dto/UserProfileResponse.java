package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Public view of another user's profile: no email, street, birth date or coordinates.
 *
 * @param common what the viewer has in common with this user; only set by {@code GET /api/user}, absent in lists
 * @param online true if active within the last minutes, false if not, null if the user hides the status
 */
public record UserProfileResponse(String referenceId,
                                  String name,
                                  String bio,
                                  Integer age,
                                  Gender gender,
                                  List<String> interests,
                                  List<String> hobbies,
                                  String city,
                                  String country,
                                  String photo,
                                  List<SongResponse> songs,
                                  List<GameResult> games,
                                  Boolean online,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) CommonResponse common) {
}
