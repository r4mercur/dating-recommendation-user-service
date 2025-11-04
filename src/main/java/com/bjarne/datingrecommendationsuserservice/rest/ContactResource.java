package com.bjarne.datingrecommendationsuserservice.rest;

import java.util.List;

import com.bjarne.datingrecommendationsuserservice.dto.ContactRequest;
import com.bjarne.datingrecommendationsuserservice.entity.Contact;
import com.bjarne.datingrecommendationsuserservice.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/contact")
public class ContactResource {
	private final ContactService contactService;

	public ContactResource(ContactService contactService) {
		this.contactService = contactService;
	}

	@GetMapping("/{referenceId}")
	public ResponseEntity<List<Contact>> getAllContactsForUser(@PathVariable String referenceId) {
		List<Contact> contacts = contactService.getAllValidContactsForUserByReferenceId(referenceId);
		return ResponseEntity.ok(contacts);
	}

	@PostMapping
	public ResponseEntity<Contact> createContact(@Valid @RequestBody ContactRequest request) {
		Contact contact = contactService.createContactFromContactRequest(request);
		return ResponseEntity.ok(contact);
	}
}
