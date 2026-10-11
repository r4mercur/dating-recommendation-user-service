package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.interaction.dto.BlockRequest;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.BlockResponse;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/blocks")
public class BlockResource {

    private final BlockService blockService;

    public BlockResource(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    public List<BlockResponse> getBlocks(@CurrentUser String referenceId) {
        return blockService.getBlocks(referenceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse block(@CurrentUser String referenceId, @Valid @RequestBody BlockRequest request) {
        return blockService.block(referenceId, request.referenceId());
    }

    @DeleteMapping("/{blockedReferenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@CurrentUser String referenceId, @PathVariable String blockedReferenceId) {
        blockService.unblock(referenceId, blockedReferenceId);
    }
}
