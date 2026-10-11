package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.games.UserGame;
import com.bjarne.datingrecommendationsuserservice.games.UserGameRepository;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.music.UserSongRepository;
import com.bjarne.datingrecommendationsuserservice.music.dto.SongResponse;
import com.bjarne.datingrecommendationsuserservice.presence.PresenceService;
import com.bjarne.datingrecommendationsuserservice.profile.CommonalityService.CardExtras;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps users to response DTOs. Photo URLs are signed on every read because they expire.
 */
@Component
public class ProfileMapper {

    private final ProfilePictureStorageService profilePictureStorageService;
    private final UserSongRepository userSongRepository;
    private final UserGameRepository userGameRepository;
    private final PresenceService presenceService;

    public ProfileMapper(ProfilePictureStorageService profilePictureStorageService,
                         UserSongRepository userSongRepository,
                         UserGameRepository userGameRepository,
                         PresenceService presenceService) {
        this.presenceService = presenceService;
        this.profilePictureStorageService = profilePictureStorageService;
        this.userSongRepository = userSongRepository;
        this.userGameRepository = userGameRepository;
    }

    public MyProfileResponse toMyProfile(User user) {
        return new MyProfileResponse(
                user.getReferenceId(),
                user.getName(),
                user.getEmail(),
                user.getBio(),
                user.getBirthDate(),
                user.getAge(),
                user.getGender(),
                user.getInterests(),
                user.getHobbies(),
                AddressDto.from(user.getAddress()),
                user.getLatitude(),
                user.getLongitude(),
                SearchPreferencesDto.from(user.getPreferences()),
                user.getStatus(),
                profilePictureStorageService.presignedUrl(user.getPhotoKey()),
                songs(user),
                games(user),
                user.isShowOnlineStatus()
        );
    }

    public UserProfileResponse toPublicProfile(User user) {
        return toPublicProfile(user, null);
    }

    /** @param common what the viewer shares with the user, null if not requested */
    public UserProfileResponse toPublicProfile(User user, CommonResponse common) {
        return new UserProfileResponse(
                user.getReferenceId(),
                user.getName(),
                user.getBio(),
                user.getAge(),
                user.getGender(),
                user.getInterests(),
                user.getHobbies(),
                user.getAddress() != null ? user.getAddress().getCity() : null,
                user.getAddress() != null ? user.getAddress().getCountry() : null,
                profilePictureStorageService.presignedUrl(user.getPhotoKey()),
                songs(user),
                games(user),
                presenceService.isOnline(user),
                common
        );
    }

    public ProfileCard toProfileCard(User user, Integer distanceKm, int compatibility, CardExtras extras) {
        return new ProfileCard(
                user.getReferenceId(),
                user.getName(),
                user.getBio(),
                user.getAge(),
                user.getGender(),
                user.getAddress() != null ? user.getAddress().getCity() : null,
                profilePictureStorageService.presignedUrl(user.getPhotoKey()),
                user.getInterests(),
                user.getHobbies(),
                distanceKm,
                compatibility,
                extras.anthem(),
                extras.commonCount(),
                presenceService.isOnline(user)
        );
    }

    private List<SongResponse> songs(User user) {
        return userSongRepository.findAllByUserIdOrderByPositionAsc(user.getId()).stream().map(SongResponse::from).toList();
    }

    private List<GameResult> games(User user) {
        return userGameRepository.findAllByUserIdOrderByPositionAsc(user.getId()).stream().map(UserGame::toGame).toList();
    }
}
