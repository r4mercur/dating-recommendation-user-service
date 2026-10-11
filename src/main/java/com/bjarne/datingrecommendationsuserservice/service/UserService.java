package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.AddressDto;
import com.bjarne.datingrecommendationsuserservice.dto.ChangePasswordRequest;
import com.bjarne.datingrecommendationsuserservice.dto.RegistrationRequest;
import com.bjarne.datingrecommendationsuserservice.dto.SearchPreferencesDto;
import com.bjarne.datingrecommendationsuserservice.dto.UpdateProfileRequest;
import com.bjarne.datingrecommendationsuserservice.entity.Address;
import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.entity.SearchPreferences;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.event.UserStatusChangedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.ConflictException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    /** Statuses a user may set on their own account. BLOCKED is reserved for moderation. */
    private static final Set<UserStatus> SELF_ASSIGNABLE_STATUSES =
            Set.of(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.MARKED_FOR_DELETION);
    private static final int MINIMUM_AGE = 18;
    private static final int MAXIMUM_AGE = 120;
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final TagService tagService;
    private final GeocodingService geocodingService;
    private final ProfilePictureStorageService profilePictureStorageService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher,
                       TagService tagService,
                       GeocodingService geocodingService,
                       ProfilePictureStorageService profilePictureStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.tagService = tagService;
        this.geocodingService = geocodingService;
        this.profilePictureStorageService = profilePictureStorageService;
    }

    public User getByReferenceId(String referenceId) {
        return userRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new NotFoundException("User not found: " + referenceId));
    }

    /**
     * Profile lookup on behalf of another user: only active accounts are visible.
     */
    public User getVisibleProfile(String referenceId) {
        User user = getByReferenceId(referenceId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new NotFoundException("User not found: " + referenceId);
        }
        return user;
    }

    public List<User> findVisibleProfiles(List<String> references) {
        return userRepository.findAllByReferenceIdInAndStatus(references, UserStatus.ACTIVE);
    }

    @Transactional
    public User register(RegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(request.password());
        user.setBio(request.bio());
        user.setBirthDate(requireAdult(request.birthDate()));
        user.setInterests(tagService.normalize(TagCategory.INTEREST, request.interests()));
        user.setHobbies(tagService.normalize(TagCategory.HOBBY, request.hobbies()));
        user.setGender(request.gender());
        user.setStatus(UserStatus.ACTIVE);
        applyAddress(user, request.address());
        if (request.preferences() != null) {
            request.preferences().applyTo(user.getPreferences());
        }

        return createUser(user);
    }

    /**
     * Persists a new user with a raw password; the search index follows via Kafka.
     * Address and coordinates must already be set (see {@link #register}).
     */
    @Transactional
    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getReferenceId() == null || user.getReferenceId().isEmpty()) {
            user.setReferenceId(UUID.randomUUID().toString());
        }

        User saved = userRepository.save(user);
        publishProfileUpdated(saved);
        return saved;
    }

    @Transactional
    public User updateProfile(String referenceId, UpdateProfileRequest request) {
        User user = getByReferenceId(referenceId);

        if (request.name() != null) user.setName(request.name().trim());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.birthDate() != null) user.setBirthDate(requireAdult(request.birthDate()));
        if (request.interests() != null) user.setInterests(tagService.normalize(TagCategory.INTEREST, request.interests()));
        if (request.hobbies() != null) user.setHobbies(tagService.normalize(TagCategory.HOBBY, request.hobbies()));
        if (request.gender() != null) user.setGender(request.gender());
        if (request.address() != null) applyAddress(user, request.address());

        User saved = userRepository.save(user);
        publishProfileUpdated(saved);
        return saved;
    }

    @Transactional
    public User updatePreferences(String referenceId, SearchPreferencesDto request) {
        User user = getByReferenceId(referenceId);
        if (user.getPreferences() == null) {
            user.setPreferences(SearchPreferences.defaults());
        }
        request.applyTo(user.getPreferences());
        User saved = userRepository.save(user);
        publishProfileUpdated(saved);
        return saved;
    }

    /**
     * Locates users that have an address but no coordinates yet (accounts from before Phase 1).
     * Addresses that cannot be located are logged and skipped.
     */
    @Transactional
    public int backfillMissingCoordinates() {
        int located = 0;
        for (User user : userRepository.findAllByLatitudeIsNullAndAddressZipCodeIsNotNull()) {
            try {
                Address address = user.getAddress();
                address.setCountry(geocodingService.normalizeCountry(address.getCountry() == null ? "DE" : address.getCountry()));
                GeocodingService.Coordinates coordinates = geocodingService.locate(address);
                user.setLatitude(coordinates.latitude());
                user.setLongitude(coordinates.longitude());
                publishProfileUpdated(userRepository.save(user));
                located++;
            } catch (BadRequestException e) {
                LOGGER.warn("Could not locate address of user {}: {}", user.getReferenceId(), e.getMessage());
            }
        }

        if (located > 0) {
            LOGGER.info("Added coordinates to {} users.", located);
        }
        return located;
    }

    @Transactional
    public void changePassword(String referenceId, ChangePasswordRequest request) {
        User user = getByReferenceId(referenceId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * Status change requested by the user themselves.
     */
    @Transactional
    public User changeOwnStatus(String referenceId, UserStatus newStatus) {
        if (!SELF_ASSIGNABLE_STATUSES.contains(newStatus)) {
            throw new BadRequestException("Status " + newStatus + " cannot be set by the user");
        }
        return changeStatus(referenceId, newStatus);
    }

    @Transactional
    public User changeStatus(String referenceId, UserStatus newStatus) {
        User user = getByReferenceId(referenceId);

        UserStatus oldStatus = user.getStatus();
        if (oldStatus == newStatus) {
            return user;
        }

        user.setStatus(newStatus);
        // the purge job deletes the account once the grace period after this point has passed
        user.setDeletionRequestedAt(newStatus == UserStatus.MARKED_FOR_DELETION ? Instant.now() : null);
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(UserStatusChangedEvent.of(referenceId, oldStatus, newStatus));

        return saved;
    }

    /**
     * The user deletes their account. It disappears right away (no login, not visible to others);
     * the data is removed by {@link AccountPurgeService} after the grace period.
     */
    @Transactional
    public void requestDeletion(String referenceId, String password) {
        User user = getByReferenceId(referenceId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("Password is incorrect");
        }
        changeStatus(referenceId, UserStatus.MARKED_FOR_DELETION);
    }

    @Transactional
    public User updatePrivacy(String referenceId, boolean showOnlineStatus) {
        User user = getByReferenceId(referenceId);
        user.setShowOnlineStatus(showOnlineStatus);
        return userRepository.save(user);
    }

    @Transactional
    public User setRole(String referenceId, Role role) {
        User user = getByReferenceId(referenceId);
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Uploads the new picture and removes the previous one from storage.
     */
    @Transactional
    public User replacePhoto(String referenceId, MultipartFile file) {
        User user = getByReferenceId(referenceId);
        String previousKey = user.getPhotoKey();

        user.setPhotoKey(profilePictureStorageService.upload(user, file));
        User saved = userRepository.save(user);

        profilePictureStorageService.delete(previousKey);
        return saved;
    }

    public Integer countUsers() {
        return Math.toIntExact(userRepository.count());
    }

    private void applyAddress(User user, AddressDto addressDto) {
        Address address = addressDto.toEntity();
        address.setCountry(geocodingService.normalizeCountry(addressDto.country()));

        GeocodingService.Coordinates coordinates = geocodingService.locate(address);
        user.setAddress(address);
        user.setLatitude(coordinates.latitude());
        user.setLongitude(coordinates.longitude());
    }

    private static LocalDate requireAdult(LocalDate birthDate) {
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (age < MINIMUM_AGE) {
            throw new BadRequestException("Users must be at least " + MINIMUM_AGE + " years old");
        }
        if (age > MAXIMUM_AGE) {
            throw new BadRequestException("Birth date is not plausible");
        }
        return birthDate;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The search index is updated asynchronously by a Kafka consumer once the transaction has committed.
     */
    private void publishProfileUpdated(User user) {
        eventPublisher.publishEvent(UserProfileUpdatedEvent.of(user.getReferenceId()));
    }
}
