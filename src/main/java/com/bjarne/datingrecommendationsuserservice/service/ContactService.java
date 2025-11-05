package com.bjarne.datingrecommendationsuserservice.service;

import java.util.List;

import com.bjarne.datingrecommendationsuserservice.dto.ContactRequest;
import com.bjarne.datingrecommendationsuserservice.entity.Contact;
import com.bjarne.datingrecommendationsuserservice.entity.ContactStatus;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.ContactRepository;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.springframework.stereotype.Service;


@Service
public class ContactService {

	private final ContactRepository contactRepository;
	private final UserRepository userRepository;

	public ContactService(ContactRepository contactRepository, UserRepository userRepository) {
		this.contactRepository = contactRepository;
		this.userRepository = userRepository;
	}

	public List<Contact> getAllValidContactsForUserByReferenceId(String referenceId) {
		if (referenceId == null || referenceId.isEmpty()) {
			throw new IllegalArgumentException("Reference ID cannot be null or empty");
		}

		User user = userRepository.findByReferenceId(referenceId);
		if (user == null) {
			throw new IllegalArgumentException("User not found for reference ID: " + referenceId);
		}

		return contactRepository.findByUserId(user.getId()).stream()
								.filter(contact -> contact.getStatus() != ContactStatus.BLOCKED).toList();
	}

	public Contact createContactFromContactRequest(ContactRequest contactRequest) {
		if (contactRequest == null) {
			throw new IllegalArgumentException("Contact request cannot be null");
		}

		User user = userRepository.findByReferenceId(contactRequest.userReferenceId());
		User contactUser = userRepository.findByReferenceId(contactRequest.contactReferenceId());
		Contact contact;

		if (user == null || contactUser == null) {
			throw new IllegalArgumentException("User or contact user not found");
		}

		if (contactRequest.status() == null) {
			contact = new Contact(user, contactUser, ContactStatus.ACCEPTED);
		} else {
			contact = new Contact(user, contactUser, contactRequest.status());
		}

		return contactRepository.save(contact);
	}
}
