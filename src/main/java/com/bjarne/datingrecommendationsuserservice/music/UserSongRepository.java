package com.bjarne.datingrecommendationsuserservice.music;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserSongRepository extends JpaRepository<UserSong, Long> {

    List<UserSong> findAllByUserIdOrderByPositionAsc(Long userId);

    List<UserSong> findAllByUserIdInOrderByPositionAsc(Collection<Long> userIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserSong s where s.userId = :userId")
    void deleteAllByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserSong s where s.userId in :userIds")
    void deleteAllByUserIdIn(Collection<Long> userIds);
}
