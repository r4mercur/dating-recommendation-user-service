package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.RecommendationResponse;
import com.bjarne.datingrecommendationsuserservice.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/recommendations")
public class RecommendationResource {

    private final RecommendationService recommendationService;

    public RecommendationResource(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<RecommendationResponse> getRecommendationsForUser(@PathVariable String userId) {
        RecommendationResponse response = recommendationService.getRecommendations(userId, 10);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}
