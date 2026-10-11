package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Postal code, city and country are required because the location is resolved from them.
 * The country may be an ISO code or a name ("DE", "Germany", "Deutschland") and is stored as ISO code.
 */
public record AddressDto(@Size(max = 255) String street,
                         @NotBlank @Size(max = 255) String city,
                         @NotBlank @Size(max = 10) String zipCode,
                         @NotBlank @Size(max = 255) String country) {

    public static AddressDto from(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDto(address.getStreet(), address.getCity(), address.getZipCode(), address.getCountry());
    }

    public Address toEntity() {
        Address address = new Address();
        address.setStreet(street);
        address.setCity(city.trim());
        address.setZipCode(zipCode.trim());
        address.setCountry(country);
        return address;
    }
}
