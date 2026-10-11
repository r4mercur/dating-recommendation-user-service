package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NormalizationTest {

    private final GeocodingService geocodingService = new GeocodingService(mock(PostalCodeRepository.class));

    @ParameterizedTest
    @CsvSource({
            "PLAYING_VIDEO_GAMES, playing_video_games",
            "Playing Video Games, playing_video_games",
            "' board-games ', board_games",
            "Hiking, hiking"
    })
    void tagsAreNormalizedToSlugs(String input, String expected) {
        assertThat(TagService.toSlug(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"DE, DE", "de, DE", "Germany, DE", "Deutschland, DE", "' austria ', AT", "Österreich, AT"})
    void countriesAreNormalizedToIsoCodes(String input, String expected) {
        assertThat(geocodingService.normalizeCountry(input)).isEqualTo(expected);
    }

    @Test
    void unknownCountryIsRejected() {
        assertThatThrownBy(() -> geocodingService.normalizeCountry("Atlantis"))
                .isInstanceOf(BadRequestException.class);
    }
}
