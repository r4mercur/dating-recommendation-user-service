package com.bjarne.datingrecommendationsuserservice.chat;

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
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByReferenceIdAndMatchId(String referenceId, Long matchId);

    /** Newest first; the caller reverses the page for display. */
    @Query("select m from Message m where m.match.id = :matchId order by m.id desc")
    List<Message> findLatest(Long matchId, Pageable pageable);

    @Query("select m from Message m where m.match.id = :matchId and m.id < :beforeId order by m.id desc")
    List<Message> findBefore(Long matchId, Long beforeId, Pageable pageable);

    /** The newest message of each given match. */
    @Query("""
            select m from Message m
            where m.id in (select max(latest.id) from Message latest where latest.match.id in :matchIds group by latest.match.id)
            """)
    List<Message> findLastMessages(Collection<Long> matchIds);

    /** [matchId, count] of unread messages the user received, per match. */
    @Query("""
            select m.match.id, count(m) from Message m
            where m.match.id in :matchIds and m.sender.id <> :userId and m.readAt is null
            group by m.match.id
            """)
    List<Object[]> countUnread(Collection<Long> matchIds, Long userId);

    /** All unread messages the user received in active conversations, for the badge. */
    @Query("""
            select count(m) from Message m
            where m.readAt is null and m.sender.id <> :userId and m.match.status = :status
              and (m.match.userLow.id = :userId or m.match.userHigh.id = :userId)
            """)
    long countAllUnread(Long userId, com.bjarne.datingrecommendationsuserservice.interaction.Match.MatchStatus status);

    /** Marks everything the other person sent in this match as read. */
    @Modifying(clearAutomatically = true)
    @Query("update Message m set m.readAt = :readAt where m.match.id = :matchId and m.sender.id <> :readerId and m.readAt is null")
    int markRead(Long matchId, Long readerId, Instant readAt);
}
