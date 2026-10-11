package com.bjarne.datingrecommendationsuserservice.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByReferenceId(String referenceId);

    Optional<Match> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    @Query("""
            select m from Match m join fetch m.userLow join fetch m.userHigh
            where m.status = :status and (m.userLow.id = :userId or m.userHigh.id = :userId)
            order by m.createdAt desc
            """)
    List<Match> findAllOfUser(Long userId, Match.MatchStatus status);

    @Modifying
    @Query("delete from Match m where m.userLow.id in :userIds or m.userHigh.id in :userIds")
    void deleteAllInvolving(Collection<Long> userIds);
}
