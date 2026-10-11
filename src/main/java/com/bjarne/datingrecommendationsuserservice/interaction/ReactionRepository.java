package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    boolean existsByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    Optional<Reaction> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    long countByFromUserIdAndTypeAndCreatedAtGreaterThanEqual(Long fromUserId, ReactionType type, Instant since);

    List<Reaction> findAllByToUserIdAndFromUserIdIn(Long toUserId, Collection<Long> fromUserIds);

    @Query("select r.toUser.referenceId from Reaction r where r.fromUser.id = :userId")
    List<String> findTargetReferenceIds(Long userId);

    /**
     * Positive reactions to the user that are still open: the user has not reacted back, nobody blocked anybody.
     */
    @Query("""
            select r from Reaction r join fetch r.fromUser sender
            where r.toUser.id = :userId and r.type in :types and sender.status = :status
              and not exists (select 1 from Reaction answer where answer.fromUser.id = :userId and answer.toUser.id = sender.id)
              and not exists (select 1 from Block b
                              where (b.blocker.id = :userId and b.blocked.id = sender.id)
                                 or (b.blocker.id = sender.id and b.blocked.id = :userId))
            order by r.createdAt desc
            """)
    List<Reaction> findOpenReceived(Long userId, Collection<ReactionType> types, UserStatus status);

    @Modifying
    @Query("delete from Reaction r where r.fromUser.id in :userIds or r.toUser.id in :userIds")
    void deleteAllInvolving(Collection<Long> userIds);
}
