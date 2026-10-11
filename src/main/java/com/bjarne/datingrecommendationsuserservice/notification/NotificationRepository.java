package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByUserIdAndSourceKey(Long userId, String sourceKey);

    Optional<Notification> findByReferenceIdAndUserId(String referenceId, Long userId);

    /**
     * Newest first, older than {@code beforeId}; only about active people the user has not blocked (or been blocked by).
     */
    @Query("""
            select n from Notification n join fetch n.actor actor
            where n.user.id = :userId and n.id < :beforeId and actor.status = :status
              and not exists (select 1 from Block b
                              where (b.blocker.id = :userId and b.blocked.id = actor.id)
                                 or (b.blocker.id = actor.id and b.blocked.id = :userId))
            order by n.id desc
            """)
    List<Notification> findVisible(Long userId, Long beforeId, UserStatus status, Pageable pageable);

    @Query("""
            select count(n) from Notification n join n.actor actor
            where n.user.id = :userId and n.readAt is null and actor.status = :status
              and not exists (select 1 from Block b
                              where (b.blocker.id = :userId and b.blocked.id = actor.id)
                                 or (b.blocker.id = actor.id and b.blocked.id = :userId))
            """)
    long countVisibleUnread(Long userId, UserStatus status);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);

    @Modifying
    @Query("delete from Notification n where n.readAt is not null and n.readAt < :cutoff")
    int deleteReadBefore(Instant cutoff);

    @Modifying
    @Query("delete from Notification n where n.user.id in :userIds or n.actor.id in :userIds")
    void deleteAllInvolving(Collection<Long> userIds);
}
