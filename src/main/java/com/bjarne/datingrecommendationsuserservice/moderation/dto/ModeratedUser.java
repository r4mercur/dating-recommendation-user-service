package com.bjarne.datingrecommendationsuserservice.moderation.dto;

import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;
import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;

/**
 * A user as moderators see them: the public profile plus account data.
 *
 * @param openReports open reports against this user
 */
public record ModeratedUser(UserProfileResponse profile, String email, UserStatus status, Role role, long openReports) {
}
