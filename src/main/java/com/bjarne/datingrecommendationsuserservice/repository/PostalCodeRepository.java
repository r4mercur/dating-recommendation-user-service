package com.bjarne.datingrecommendationsuserservice.repository;

import com.bjarne.datingrecommendationsuserservice.entity.PostalCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostalCodeRepository extends JpaRepository<PostalCode, Long> {
    List<PostalCode> findAllByCountryCodeAndPostalCodeOrderByIdAsc(String countryCode, String postalCode);
    List<PostalCode> findAllByCountryCodeAndPlaceNameIgnoreCaseOrderByIdAsc(String countryCode, String placeName);
    boolean existsByCountryCode(String countryCode);
    long countByCountryCode(String countryCode);

    /**
     * Regular places (no bulk receivers like companies or authorities) in a stable order, so that dev data
     * picked with a seeded random is the same on every fresh database.
     */
    @Query(value = """
            select * from postal_codes
            where country_code = :countryCode and place_name not like '% %' and place_name not like '%-%'
            order by postal_code, place_name
            """, nativeQuery = true)
    List<PostalCode> findRegularPlaces(String countryCode);
}
