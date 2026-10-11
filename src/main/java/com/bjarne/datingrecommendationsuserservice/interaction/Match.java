package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Two users reacted positively to each other. The pair is stored ordered by user id (one match per pair).
 * An ended match stays for history; the reactions remain, so the pair cannot match again.
 */
@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String referenceId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_low_id")
    private User userLow;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_high_id")
    private User userHigh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant endedAt;

    @ManyToOne
    @JoinColumn(name = "ended_by_id")
    private User endedBy;

    /** Time of the newest chat message, used to sort conversations. */
    @Setter
    private Instant lastMessageAt;

    public static Match between(User one, User other) {
        Match match = new Match();
        match.referenceId = UUID.randomUUID().toString();
        match.userLow = one.getId() < other.getId() ? one : other;
        match.userHigh = one.getId() < other.getId() ? other : one;
        match.status = MatchStatus.ACTIVE;
        match.createdAt = Instant.now();
        return match;
    }

    public boolean isActive() {
        return status == MatchStatus.ACTIVE;
    }

    public boolean involves(User user) {
        return userLow.getId().equals(user.getId()) || userHigh.getId().equals(user.getId());
    }

    public User partnerOf(User user) {
        return userLow.getId().equals(user.getId()) ? userHigh : userLow;
    }

    public void end(User by) {
        this.status = MatchStatus.ENDED;
        this.endedAt = Instant.now();
        this.endedBy = by;
    }

    public enum MatchStatus {
        ACTIVE,
        ENDED
    }
}
