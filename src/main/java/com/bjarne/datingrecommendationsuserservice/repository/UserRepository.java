package com.bjarne.datingrecommendationsuserservice.repository;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByReferenceId(String referenceId);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findAllByReferenceIdInAndStatus(Collection<String> references, UserStatus status);
    Page<User> findAllByStatus(UserStatus status, Pageable pageable);
    List<User> findAllByLatitudeIsNullAndAddressZipCodeIsNotNull();

    List<User> findAllByStatusAndDeletionRequestedAtBefore(UserStatus status, Instant cutoff);

    @Transactional
    @Modifying
    @Query("update User u set u.lastActiveAt = :now where u.referenceId = :referenceId")
    int touchLastActive(String referenceId, Instant now);

    /**
     * Row locks on the given users until the transaction ends, always in id order to avoid deadlocks.
     * Serializes operations on a pair of users, e.g. two simultaneous likes that must create exactly one match.
     */
    @Query(value = "select id from users where id in (:ids) order by id for update", nativeQuery = true)
    List<Long> lockAllById(Collection<Long> ids);
}
