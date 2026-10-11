package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.ChangePasswordRequest;
import com.bjarne.datingrecommendationsuserservice.dto.ChangeStatusRequest;
import com.bjarne.datingrecommendationsuserservice.dto.DeleteAccountRequest;
import com.bjarne.datingrecommendationsuserservice.dto.MyProfileResponse;
import com.bjarne.datingrecommendationsuserservice.dto.PrivacyRequest;
import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.dto.SearchPreferencesDto;
import com.bjarne.datingrecommendationsuserservice.dto.UpdateProfileRequest;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.security.RefreshTokenService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints of the logged-in user. The user is always taken from the JWT, never from the request.
 */
@RestController
@RequestMapping("/api/me")
public class MeResource {
    private final UserService userService;
    private final ProfileMapper profileMapper;
    private final RateLimiter rateLimiter;
    private final RefreshTokenService refreshTokenService;

    public MeResource(UserService userService, ProfileMapper profileMapper, RateLimiter rateLimiter,
                      RefreshTokenService refreshTokenService) {
        this.rateLimiter = rateLimiter;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.profileMapper = profileMapper;
    }

    @GetMapping
    public MyProfileResponse getProfile(@CurrentUser String referenceId) {
        return profileMapper.toMyProfile(userService.getByReferenceId(referenceId));
    }

    @PatchMapping
    public MyProfileResponse updateProfile(@CurrentUser String referenceId,
                                           @Valid @RequestBody UpdateProfileRequest request) {
        return profileMapper.toMyProfile(userService.updateProfile(referenceId, request));
    }

    @GetMapping("/preferences")
    public SearchPreferencesDto getPreferences(@CurrentUser String referenceId) {
        return SearchPreferencesDto.from(userService.getByReferenceId(referenceId).getPreferences());
    }

    @PutMapping("/preferences")
    public SearchPreferencesDto updatePreferences(@CurrentUser String referenceId,
                                                  @Valid @RequestBody SearchPreferencesDto request) {
        return SearchPreferencesDto.from(userService.updatePreferences(referenceId, request).getPreferences());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@CurrentUser String referenceId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        rateLimiter.check(RateLimiter.PASSWORD_CHANGE, referenceId);
        userService.changePassword(referenceId, request);
        // other devices are logged out once their access token expires
        refreshTokenService.revokeAll(referenceId);
    }

    @PutMapping("/privacy")
    public MyProfileResponse updatePrivacy(@CurrentUser String referenceId, @Valid @RequestBody PrivacyRequest request) {
        return profileMapper.toMyProfile(userService.updatePrivacy(referenceId, request.showOnlineStatus()));
    }

    /**
     * Deletes the own account (GDPR). It is gone for everybody right away; the data is purged after the grace
     * period (app.account-deletion.grace-period).
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@CurrentUser String referenceId, @Valid @RequestBody DeleteAccountRequest request) {
        rateLimiter.check(RateLimiter.PASSWORD_CHANGE, referenceId);
        userService.requestDeletion(referenceId, request.password());
    }

    @PatchMapping("/status")
    public MyProfileResponse changeStatus(@CurrentUser String referenceId,
                                          @Valid @RequestBody ChangeStatusRequest request) {
        return profileMapper.toMyProfile(userService.changeOwnStatus(referenceId, request.status()));
    }
}
