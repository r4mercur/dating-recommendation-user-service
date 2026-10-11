package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.entity.Address;
import com.bjarne.datingrecommendationsuserservice.entity.PostalCode;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves coordinates from the address the user entered, using the local postal code table.
 * No external geocoding API and no browser location are used.
 */
@Service
public class GeocodingService {

    private final PostalCodeRepository postalCodeRepository;

    public GeocodingService(PostalCodeRepository postalCodeRepository) {
        this.postalCodeRepository = postalCodeRepository;
    }

    public record Coordinates(double latitude, double longitude) {
    }

    /**
     * Normalizes the country to an ISO 3166 alpha-2 code ("Germany", "Deutschland", "de" -> "DE").
     */
    public String normalizeCountry(String country) {
        String value = country.trim();
        if (value.length() == 2) {
            return value.toUpperCase(Locale.ROOT);
        }

        for (String code : Locale.getISOCountries()) {
            Locale locale = Locale.of("", code);
            if (value.equalsIgnoreCase(locale.getDisplayCountry(Locale.ENGLISH))
                    || value.equalsIgnoreCase(locale.getDisplayCountry(Locale.GERMAN))) {
                return code;
            }
        }

        throw new BadRequestException("Unknown country: " + country);
    }

    /**
     * Looks up the postal code first; the city decides between multiple places with the same postal code
     * and serves as fallback when the postal code is unknown.
     */
    public Coordinates locate(Address address) {
        String countryCode = address.getCountry();
        if (!postalCodeRepository.existsByCountryCode(countryCode)) {
            throw new BadRequestException("Country is not supported yet: " + countryCode);
        }

        String postalCode = address.getZipCode() == null ? "" : address.getZipCode().trim();
        String city = address.getCity() == null ? "" : address.getCity().trim();

        List<PostalCode> byPostalCode = postalCodeRepository.findAllByCountryCodeAndPostalCodeOrderByIdAsc(countryCode, postalCode);
        Optional<PostalCode> match = byPostalCode.stream()
                .filter(place -> place.getPlaceName().equalsIgnoreCase(city))
                .findFirst()
                .or(() -> byPostalCode.stream().findFirst())
                .or(() -> postalCodeRepository.findAllByCountryCodeAndPlaceNameIgnoreCaseOrderByIdAsc(countryCode, city).stream().findFirst());

        return match
                .map(place -> new Coordinates(place.getLatitude(), place.getLongitude()))
                .orElseThrow(() -> new BadRequestException("Address could not be located, please check postal code and city"));
    }
}
