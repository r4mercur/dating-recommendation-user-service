package com.bjarne.datingrecommendationsuserservice.games;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserGameRepository extends JpaRepository<UserGame, Long> {

    List<UserGame> findAllByUserIdOrderByPositionAsc(Long userId);

    List<UserGame> findAllByUserIdInOrderByPositionAsc(Collection<Long> userIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserGame g where g.userId = :userId")
    void deleteAllByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserGame g where g.userId in :userIds")
    void deleteAllByUserIdIn(Collection<Long> userIds);
}
