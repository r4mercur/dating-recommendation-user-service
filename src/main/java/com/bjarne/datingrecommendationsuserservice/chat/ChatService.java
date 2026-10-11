package com.bjarne.datingrecommendationsuserservice.chat;

import com.bjarne.datingrecommendationsuserservice.chat.dto.ConversationResponse;
import com.bjarne.datingrecommendationsuserservice.chat.dto.MessagePage;
import com.bjarne.datingrecommendationsuserservice.chat.dto.MessageResponse;
import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.event.ChatMessageSentEvent;
import com.bjarne.datingrecommendationsuserservice.event.ChatMessagesReadEvent;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.interaction.Match;
import com.bjarne.datingrecommendationsuserservice.interaction.MatchRepository;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chats between matched users. A conversation is the active match; it ends with an unmatch or block.
 * Messages are stored first; delivery to connected clients follows via Kafka (see {@link ChatFanoutConsumer}).
 */
@Service
public class ChatService {

    static final int MAX_CONTENT_LENGTH = 2000;

    private final MessageRepository messageRepository;
    private final MatchRepository matchRepository;
    private final UserService userService;
    private final ProfileMapper profileMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;

    public ChatService(MessageRepository messageRepository,
                       MatchRepository matchRepository,
                       UserService userService,
                       ProfileMapper profileMapper,
                       ApplicationEventPublisher eventPublisher,
                       RateLimiter rateLimiter) {
        this.messageRepository = messageRepository;
        this.matchRepository = matchRepository;
        this.userService = userService;
        this.profileMapper = profileMapper;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
    }

    /**
     * All conversations of the user with last message and unread count, most recent activity first.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(String referenceId) {
        User me = userService.getByReferenceId(referenceId);
        List<Match> matches = matchRepository.findAllOfUser(me.getId(), Match.MatchStatus.ACTIVE).stream()
                .filter(match -> match.partnerOf(me).getStatus() == UserStatus.ACTIVE)
                .toList();
        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = matches.stream().map(Match::getId).toList();
        Map<Long, Message> lastMessages = messageRepository.findLastMessages(matchIds).stream()
                .collect(Collectors.toMap(message -> message.getMatch().getId(), Function.identity()));
        Map<Long, Long> unread = messageRepository.countUnread(matchIds, me.getId()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return matches.stream()
                .sorted(Comparator.comparing((Match match) -> Optional.ofNullable(match.getLastMessageAt()).orElse(match.getCreatedAt())).reversed())
                .map(match -> {
                    Message last = lastMessages.get(match.getId());
                    return new ConversationResponse(
                            match.getReferenceId(),
                            profileMapper.toPublicProfile(match.partnerOf(me)),
                            last == null ? null : MessageResponse.from(last, match.getReferenceId()),
                            unread.getOrDefault(match.getId(), 0L),
                            match.getCreatedAt());
                })
                .toList();
    }

    /**
     * A page of the history, oldest first. Without {@code before} the newest messages, otherwise the ones before it.
     */
    @Transactional(readOnly = true)
    public MessagePage getMessages(String referenceId, String matchReferenceId, String before, int size) {
        User me = userService.getByReferenceId(referenceId);
        Match match = conversation(me, matchReferenceId);
        PageRequest page = PageRequest.of(0, size + 1);

        List<Message> messages = before == null
                ? messageRepository.findLatest(match.getId(), page)
                : messageRepository.findBefore(match.getId(), messageRepository.findByReferenceIdAndMatchId(before, match.getId())
                        .orElseThrow(() -> new NotFoundException("Message not found: " + before)).getId(), page);

        boolean hasMore = messages.size() > size;
        List<MessageResponse> items = new ArrayList<>(messages.stream()
                .limit(size)
                .map(message -> MessageResponse.from(message, match.getReferenceId()))
                .toList());
        Collections.reverse(items);
        return new MessagePage(items, hasMore);
    }

    @Transactional
    public MessageResponse send(String referenceId, String matchReferenceId, String content) {
        rateLimiter.check(RateLimiter.CHAT_MESSAGES, referenceId);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new BadRequestException("Message must not be empty");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Message must not be longer than " + MAX_CONTENT_LENGTH + " characters");
        }

        User me = userService.getByReferenceId(referenceId);
        if (me.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Only active profiles can send messages");
        }
        Match match = conversation(me, matchReferenceId);

        Message message = messageRepository.save(new Message(match, me, text));
        match.setLastMessageAt(message.getCreatedAt());
        matchRepository.save(match);

        eventPublisher.publishEvent(new ChatMessageSentEvent(message.getReferenceId(), match.getReferenceId(),
                me.getReferenceId(), match.partnerOf(me).getReferenceId(), message.getCreatedAt()));
        return MessageResponse.from(message, match.getReferenceId());
    }

    /**
     * Marks all messages the partner sent in this conversation as read and notifies the partner.
     */
    @Transactional
    public void markRead(String referenceId, String matchReferenceId) {
        User me = userService.getByReferenceId(referenceId);
        Match match = conversation(me, matchReferenceId);

        Instant readAt = Instant.now();
        if (messageRepository.markRead(match.getId(), me.getId(), readAt) > 0) {
            eventPublisher.publishEvent(new ChatMessagesReadEvent(match.getReferenceId(), me.getReferenceId(),
                    match.partnerOf(me).getReferenceId(), readAt));
        }
    }

    /**
     * The active match of the user with an active partner; everything else looks like it does not exist.
     */
    private Match conversation(User me, String matchReferenceId) {
        return matchRepository.findByReferenceId(matchReferenceId)
                .filter(match -> match.isActive() && match.involves(me) && match.partnerOf(me).getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Conversation not found: " + matchReferenceId));
    }
}
