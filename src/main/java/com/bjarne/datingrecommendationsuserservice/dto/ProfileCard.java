package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;

import java.util.List;

/**
 * A recommended profile, ready to be shown as card.
 *
 * @param distanceKm    rounded distance between both postal code locations, null if one of them has no location
 * @param compatibility share of common tags (interests and hobbies) in percent, 0-100
 * @param anthem        the profile song, null if the user did not pick one
 * @param commonCount   number of shared interests, hobbies, artists and games
 * @param online        true if active within the last minutes, false if not, null if the user hides the status
 */
public record ProfileCard(String referenceId,
                          String name,
                          String bio,
                          Integer age,
                          Gender gender,
                          String city,
                          String photo,
                          List<String> interests,
                          List<String> hobbies,
                          Integer distanceKm,
                          int compatibility,
                          SongResponse anthem,
                          int commonCount,
                          Boolean online) {
}
