package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String referenceId;

    private String name;
    private String email;
    private String password;

    @Column(length = 1000)
    private String bio;

    private LocalDate birthDate;

    /** Tag slugs from the catalog (category INTEREST). */
    private List<String> interests;
    /** Tag slugs from the catalog (category HOBBY). */
    private List<String> hobbies;

    @Embedded
    private Address address;
    /** Resolved from the address' postal code, never from the client. */
    private Double latitude;
    private Double longitude;

    @Embedded
    private SearchPreferences preferences = SearchPreferences.defaults();

    @Enumerated(EnumType.STRING)
    private Gender gender;
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /** When the user asked for deletion (status MARKED_FOR_DELETION); the account is purged after the grace period. */
    private Instant deletionRequestedAt;

    /** Last API request or open WebSocket, written at most once a minute (see PresenceService). */
    private Instant lastActiveAt;

    /** Privacy: whether others may see if the user is online. */
    @Column(nullable = false)
    private boolean showOnlineStatus = true;

    /** Object key of the profile picture in the S3 storage; URLs are signed on read. */
    private String photoKey;

    public Integer getAge() {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
