package com.bjarne.datingrecommendationsuserservice.entity;

import com.bjarne.datingrecommendationsuserservice.dto.UserRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String referenceId;

    private String name;
    private String email;
    private String password;

    private List<String> interests;
    private List<String> hobbies;

    private Integer age;

    @Embedded
    private Address address;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    @Column(columnDefinition = "TEXT")
    private String photo;

    public User fromUserRequest(UserRequest userRequest, String password) {
        this.name = userRequest.name();
        this.email = userRequest.email();
        this.password = password;
        this.interests = userRequest.interests();
        this.hobbies = userRequest.hobbies();
        this.age = userRequest.age();
        this.address = userRequest.address();
        this.referenceId = UUID.randomUUID().toString();
        return this;
    }
}
