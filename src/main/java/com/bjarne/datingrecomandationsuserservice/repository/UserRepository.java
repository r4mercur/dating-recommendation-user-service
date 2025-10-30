package com.bjarne.datingrecomandationsuserservice.repository;

import com.bjarne.datingrecomandationsuserservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByReferenceId(String referenceId);
    User findByEmail(String email);
    List<User> findAllByReferenceIdIn(List<String> references);
}
