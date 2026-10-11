package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.dto.RecommendationPage;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/me/recommendations")
public class RecommendationResource {

    private final RecommendationService recommendationService;

    public RecommendationResource(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * First page without cursor, further pages with the nextCursor of the previous response.
     */
    @GetMapping
    public RecommendationPage getRecommendations(@CurrentUser String referenceId,
                                                 @RequestParam(value = "cursor", required = false) String cursor,
                                                 @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(50) int size) {
        return recommendationService.getRecommendations(referenceId, cursor, size);
    }
}
