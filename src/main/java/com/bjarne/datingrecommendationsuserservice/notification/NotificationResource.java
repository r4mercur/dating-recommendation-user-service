package com.bjarne.datingrecommendationsuserservice.notification;

import com.bjarne.datingrecommendationsuserservice.notification.dto.NotificationPage;
import com.bjarne.datingrecommendationsuserservice.notification.dto.UnreadCounts;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * New notifications are also pushed via WebSocket to /user/queue/notifications.
 */
@RestController
@RequestMapping("/api/me/notifications")
public class NotificationResource {

    private final NotificationService notificationService;

    public NotificationResource(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPage getNotifications(@CurrentUser String referenceId,
                                             @RequestParam(value = "before", required = false) String before,
                                             @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.getNotifications(referenceId, before, size);
    }

    /** Badge counters: unread notifications and unread chat messages. */
    @GetMapping("/unread-count")
    public UnreadCounts getUnreadCounts(@CurrentUser String referenceId) {
        return notificationService.getUnreadCounts(referenceId);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@CurrentUser String referenceId) {
        notificationService.markAllRead(referenceId);
    }

    @PostMapping("/{notificationReferenceId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentUser String referenceId, @PathVariable String notificationReferenceId) {
        notificationService.markRead(referenceId, notificationReferenceId);
    }
}
