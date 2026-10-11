package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.interaction.dto.MatchResponse;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/matches")
public class MatchResource {

    private final MatchService matchService;

    public MatchResource(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public List<MatchResponse> getMatches(@CurrentUser String referenceId) {
        return matchService.getMatches(referenceId);
    }

    @DeleteMapping("/{matchReferenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@CurrentUser String referenceId, @PathVariable String matchReferenceId) {
        matchService.unmatch(referenceId, matchReferenceId);
    }
}
