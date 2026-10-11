package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.MyProfileResponse;
import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.dto.RegistrationRequest;
import com.bjarne.datingrecommendationsuserservice.dto.UserProfileResponse;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.interaction.BlockService;
import com.bjarne.datingrecommendationsuserservice.profile.CommonalityService;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserResource {
    private final UserService userService;
    private final BlockService blockService;
    private final ProfileMapper profileMapper;
    private final CommonalityService commonalityService;
    private final RateLimiter rateLimiter;

    public UserResource(UserService userService, BlockService blockService, ProfileMapper profileMapper,
                        CommonalityService commonalityService, RateLimiter rateLimiter) {
        this.userService = userService;
        this.blockService = blockService;
        this.profileMapper = profileMapper;
        this.commonalityService = commonalityService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Public profile of an active user including what the viewer has in common with them; users who blocked
     * each other do not see each other.
     */
    @GetMapping
    public UserProfileResponse getUser(@CurrentUser String viewerReferenceId, @RequestParam("referenceId") String referenceId) {
        User profile = userService.getVisibleProfile(referenceId);
        User viewer = userService.getByReferenceId(viewerReferenceId);
        if (blockService.isBlockedEitherWay(viewer, profile)) {
            throw new NotFoundException("User not found: " + referenceId);
        }
        return profileMapper.toPublicProfile(profile, commonalityService.between(viewer, profile));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyProfileResponse register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(RateLimiter.REGISTRATION_IP, httpRequest.getRemoteAddr());
        return profileMapper.toMyProfile(userService.register(request));
    }
}
