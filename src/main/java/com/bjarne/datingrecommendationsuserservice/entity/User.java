package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
    private Gender gender;
    private UserStatus status;

    private String photo;
}
