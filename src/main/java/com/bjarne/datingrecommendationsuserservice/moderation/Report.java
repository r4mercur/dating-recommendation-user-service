package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A user reported another user. The reporter is null once they deleted their account.
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String referenceId;

    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reported_id")
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @ManyToOne
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(length = 1000)
    private String resolutionNote;

    public Report(User reporter, User reported, ReportReason reason, String comment) {
        this.referenceId = UUID.randomUUID().toString();
        this.reporter = reporter;
        this.reported = reported;
        this.reason = reason;
        this.comment = comment;
        this.status = ReportStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void close(ReportStatus status, User moderator, String note) {
        this.status = status;
        this.resolvedAt = Instant.now();
        this.resolvedBy = moderator;
        this.resolutionNote = note;
    }
}
