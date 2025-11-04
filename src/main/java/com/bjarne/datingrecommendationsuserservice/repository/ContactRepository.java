package com.bjarne.datingrecommendationsuserservice.repository;

import java.util.List;

import com.bjarne.datingrecommendationsuserservice.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
	List<Contact> findByUserId(Long userId);
}
