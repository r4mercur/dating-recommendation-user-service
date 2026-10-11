package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionRequest;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionResponse;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReactionTypeResponse;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.ReceivedLikeResponse;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class ReactionResource {

    private final ReactionService reactionService;

    public ReactionResource(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    /**
     * Like, super like, compliment or pass. The response contains the match if this reaction created one.
     */
    @PostMapping("/reactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReactionResponse react(@CurrentUser String referenceId, @Valid @RequestBody ReactionRequest request) {
        return reactionService.react(referenceId, request);
    }

    @GetMapping("/reactions/types")
    public List<ReactionTypeResponse> getReactionTypes(@CurrentUser String referenceId) {
        return reactionService.getReactionTypes(referenceId);
    }

    @GetMapping("/likes-received")
    public List<ReceivedLikeResponse> getReceivedLikes(@CurrentUser String referenceId) {
        return reactionService.getReceivedLikes(referenceId);
    }
}
