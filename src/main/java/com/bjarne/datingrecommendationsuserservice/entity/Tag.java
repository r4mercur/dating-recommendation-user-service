package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagCategory category;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(nullable = false, length = 128)
    private String label;
}
