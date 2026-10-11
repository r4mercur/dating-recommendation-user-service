package com.bjarne.datingrecommendationsuserservice.interaction;

import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.event.MatchEndedEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.interaction.dto.BlockResponse;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Blocks hide two users from each other everywhere: recommendations, profiles, reactions and matches.
 */
@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MatchService matchService;
    private final ProfileMapper profileMapper;

    public BlockService(BlockRepository blockRepository,
                        UserRepository userRepository,
                        UserService userService,
                        MatchService matchService,
                        ProfileMapper profileMapper) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.matchService = matchService;
        this.profileMapper = profileMapper;
    }

    /**
     * Blocks the user and ends an active match between both. Blocking twice has no further effect.
     */
    @Transactional
    public BlockResponse block(String referenceId, String targetReferenceId) {
        if (referenceId.equals(targetReferenceId)) {
            throw new BadRequestException("Users cannot block themselves");
        }

        User me = userService.getByReferenceId(referenceId);
        User target = userService.getByReferenceId(targetReferenceId);
        userRepository.lockAllById(List.of(me.getId(), target.getId()));

        Block block = blockRepository.findByBlockerIdAndBlockedId(me.getId(), target.getId())
                .orElseGet(() -> blockRepository.save(new Block(me, target)));
        matchService.endBetween(me, target, me, MatchEndedEvent.Reason.BLOCKED);

        return toResponse(block);
    }

    @Transactional
    public void unblock(String referenceId, String targetReferenceId) {
        User me = userService.getByReferenceId(referenceId);
        User target = userService.getByReferenceId(targetReferenceId);
        blockRepository.findByBlockerIdAndBlockedId(me.getId(), target.getId()).ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> getBlocks(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        return blockRepository.findAllByBlocker(me.getId()).stream().map(this::toResponse).toList();
    }

    public boolean isBlockedEitherWay(User user, User other) {
        return blockRepository.existsBetween(user.getId(), other.getId());
    }

    public Set<String> blockedEitherWay(User user) {
        return Set.copyOf(blockRepository.findReferenceIdsBlockedEitherWay(user.getId()));
    }

    private BlockResponse toResponse(Block block) {
        return new BlockResponse(profileMapper.toPublicProfile(block.getBlocked()), block.getCreatedAt());
    }
}
