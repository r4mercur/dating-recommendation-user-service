package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * What one user thinks of another. There is at most one reaction per direction; it cannot be changed.
 */
@Entity
@Table(name = "reactions")
@Getter
@NoArgsConstructor
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_user_id")
    private User fromUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_user_id")
    private User toUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReactionType type;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    public Reaction(User fromUser, User toUser, ReactionType type, String message) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.type = type;
        this.message = message;
        this.createdAt = Instant.now();
    }
}
