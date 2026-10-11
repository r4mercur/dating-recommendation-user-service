package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String referenceId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    /** For NEW_LIKE: LIKE, SUPER_LIKE or COMPLIMENT. */
    @Column(length = 30)
    private String reactionType;

    /** For NEW_MATCH: opens the conversation. */
    @Column(length = 36)
    private String matchReferenceId;

    @Column(nullable = false, length = 100)
    private String sourceKey;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    Notification(User user, NotificationType type, User actor, String reactionType, String matchReferenceId, String sourceKey) {
        this.referenceId = UUID.randomUUID().toString();
        this.user = user;
        this.type = type;
        this.actor = actor;
        this.reactionType = reactionType;
        this.matchReferenceId = matchReferenceId;
        this.sourceKey = sourceKey;
        this.createdAt = Instant.now();
    }

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public static Notification like(User user, User actor, String reactionType) {
        return new Notification(user, NotificationType.NEW_LIKE, actor, reactionType, null, "like:" + actor.getReferenceId());
    }

    public static Notification match(User user, User partner, String matchReferenceId) {
        return new Notification(user, NotificationType.NEW_MATCH, partner, null, matchReferenceId, "match:" + matchReferenceId);
    }
}
