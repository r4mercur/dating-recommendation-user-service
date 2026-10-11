package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.UpdateProfileRequest;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.UserProfileUpdatedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TagService tagService = mock(TagService.class);
    private final GeocodingService geocodingService = mock(GeocodingService.class);
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, eventPublisher,
                tagService, geocodingService, mock(ProfilePictureStorageService.class));

        user = new User();
        user.setReferenceId("ref-1");
        user.setName("Alex");
        user.setPassword("hashed");
        user.setBirthDate(LocalDate.now().minusYears(25));
        user.setInterests(List.of("music"));
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByReferenceId("ref-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateProfileKeepsReferenceIdAndPasswordAndOnlyChangesGivenFields() {
        when(tagService.normalize(eq(TagCategory.HOBBY), any())).thenReturn(List.of("climbing"));
        LocalDate birthDate = LocalDate.now().minusYears(30);

        User updated = userService.updateProfile("ref-1",
                new UpdateProfileRequest("Alexandra", null, birthDate, null, List.of("Climbing"), null, null));

        assertThat(updated.getReferenceId()).isEqualTo("ref-1");
        assertThat(updated.getPassword()).isEqualTo("hashed");
        assertThat(updated.getName()).isEqualTo("Alexandra");
        assertThat(updated.getInterests()).containsExactly("music");
        assertThat(updated.getHobbies()).containsExactly("climbing");
        assertThat(updated.getAge()).isEqualTo(30);
        verifyNoInteractions(passwordEncoder, geocodingService);
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof UserProfileUpdatedEvent profileEvent && profileEvent.referenceId().equals("ref-1")));
    }

    @Test
    void birthDateMustBeAdult() {
        UpdateProfileRequest request = new UpdateProfileRequest(null, null, LocalDate.now().minusYears(17), null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("ref-1", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void usersCannotBlockThemselves() {
        assertThatThrownBy(() -> userService.changeOwnStatus("ref-1", UserStatus.BLOCKED))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeStatusPublishesEvent() {
        userService.changeOwnStatus("ref-1", UserStatus.INACTIVE);

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(eventPublisher).publishEvent(any(Object.class));
    }
}
