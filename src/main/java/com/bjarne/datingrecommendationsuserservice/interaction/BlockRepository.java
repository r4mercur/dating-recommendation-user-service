package com.bjarne.datingrecommendationsuserservice.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker.id = :userId and b.blocked.id = :otherId) or (b.blocker.id = :otherId and b.blocked.id = :userId)
            """)
    boolean existsBetween(Long userId, Long otherId);

    /** referenceIds of everybody the user blocked or was blocked by. */
    @Query("""
            select case when b.blocker.id = :userId then b.blocked.referenceId else b.blocker.referenceId end
            from Block b where b.blocker.id = :userId or b.blocked.id = :userId
            """)
    List<String> findReferenceIdsBlockedEitherWay(Long userId);

    @Query("select b from Block b join fetch b.blocked where b.blocker.id = :userId order by b.createdAt desc")
    List<Block> findAllByBlocker(Long userId);

    @Modifying
    @Query("delete from Block b where b.blocker.id in :userIds or b.blocked.id in :userIds")
    void deleteAllInvolving(Collection<Long> userIds);
}
