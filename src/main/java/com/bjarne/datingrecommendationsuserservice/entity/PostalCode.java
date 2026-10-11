package com.bjarne.datingrecommendationsuserservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only geocoding data (GeoNames, imported by Flyway).
 */
@Entity
@Table(name = "postal_codes")
@Immutable
@Getter
@NoArgsConstructor
public class PostalCode {

    @Id
    private Long id;

    @Column(length = 2)
    private String countryCode;

    @Column(length = 10)
    private String postalCode;

    @Column(length = 200)
    private String placeName;

    @Column(length = 100)
    private String state;

    private double latitude;
    private double longitude;
}
