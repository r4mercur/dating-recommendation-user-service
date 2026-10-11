package com.bjarne.datingrecommendationsuserservice.chat;

import com.bjarne.datingrecommendationsuserservice.chat.dto.ChatError;
import com.bjarne.datingrecommendationsuserservice.chat.dto.ChatReadCommand;
import com.bjarne.datingrecommendationsuserservice.chat.dto.ChatSendCommand;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Chat commands over STOMP. The principal name is the user's referenceId (JWT subject).
 * Results are not returned here: stored messages reach both users via Kafka and /user/queue/messages.
 */
@Controller
public class ChatStompController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStompController.class);

    private final ChatService chatService;

    public ChatStompController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendCommand command, Principal principal) {
        chatService.send(principal.getName(), command.matchReferenceId(), command.content());
    }

    @MessageMapping("/chat.read")
    public void read(@Payload ChatReadCommand command, Principal principal) {
        chatService.markRead(principal.getName(), command.matchReferenceId());
    }

    @MessageExceptionHandler({BadRequestException.class, NotFoundException.class})
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handleKnownError(RuntimeException ex) {
        return new ChatError(ex.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handleUnexpected(Exception ex) {
        LOGGER.error("Unhandled STOMP error", ex);
        return new ChatError("Unexpected server error");
    }
}
