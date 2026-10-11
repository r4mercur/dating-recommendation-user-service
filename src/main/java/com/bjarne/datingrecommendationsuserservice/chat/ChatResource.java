package com.bjarne.datingrecommendationsuserservice.chat;

import com.bjarne.datingrecommendationsuserservice.chat.dto.ConversationResponse;
import com.bjarne.datingrecommendationsuserservice.chat.dto.MessagePage;
import com.bjarne.datingrecommendationsuserservice.chat.dto.MessageResponse;
import com.bjarne.datingrecommendationsuserservice.chat.dto.SendMessageRequest;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Conversations are identified by the match referenceId. Sending works over REST and STOMP (/app/chat.send);
 * new messages are pushed to both users via /user/queue/messages either way.
 */
@RestController
@RequestMapping("/api/me/conversations")
public class ChatResource {

    private final ChatService chatService;

    public ChatResource(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ConversationResponse> getConversations(@CurrentUser String referenceId) {
        return chatService.getConversations(referenceId);
    }

    @GetMapping("/{matchReferenceId}/messages")
    public MessagePage getMessages(@CurrentUser String referenceId,
                                   @PathVariable String matchReferenceId,
                                   @RequestParam(value = "before", required = false) String before,
                                   @RequestParam(value = "size", defaultValue = "30") @Min(1) @Max(100) int size) {
        return chatService.getMessages(referenceId, matchReferenceId, before, size);
    }

    @PostMapping("/{matchReferenceId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@CurrentUser String referenceId,
                                @PathVariable String matchReferenceId,
                                @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(referenceId, matchReferenceId, request.content());
    }

    @PostMapping("/{matchReferenceId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentUser String referenceId, @PathVariable String matchReferenceId) {
        chatService.markRead(referenceId, matchReferenceId);
    }
}
