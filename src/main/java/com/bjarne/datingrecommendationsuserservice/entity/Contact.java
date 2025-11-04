package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
public class Contact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne
	@JoinColumn(name = "contact_user_id")
	private User contactUser;

	@Enumerated(EnumType.STRING)
	private ContactStatus status;

	public Contact(User user, User contactUser, ContactStatus status) {
		this.user = user;
		this.contactUser = contactUser;
		this.status = status;
	}
}
