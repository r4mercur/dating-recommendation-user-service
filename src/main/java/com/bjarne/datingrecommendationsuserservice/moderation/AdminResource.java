package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.moderation.dto.AdminReportResponse;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ModeratedUser;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ResolveReportRequest;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.UserRoleRequest;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.UserStatusRequest;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Moderation. Only for ROLE_ADMIN (see SecurityConfiguration).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminResource {

    private final ModerationService moderationService;

    public AdminResource(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/reports")
    public List<AdminReportResponse> getReports(@RequestParam(value = "status", defaultValue = "OPEN") ReportStatus status) {
        return moderationService.getReports(status);
    }

    @PostMapping("/reports/{reportReferenceId}/resolve")
    public AdminReportResponse resolve(@CurrentUser String adminReferenceId,
                                       @PathVariable String reportReferenceId,
                                       @Valid @RequestBody ResolveReportRequest request) {
        return moderationService.resolve(adminReferenceId, reportReferenceId, request);
    }

    @PutMapping("/users/{referenceId}/status")
    public ModeratedUser setStatus(@CurrentUser String adminReferenceId,
                                   @PathVariable String referenceId,
                                   @Valid @RequestBody UserStatusRequest request) {
        return moderationService.setStatus(adminReferenceId, referenceId, request.status());
    }

    @PutMapping("/users/{referenceId}/role")
    public ModeratedUser setRole(@CurrentUser String adminReferenceId,
                                 @PathVariable String referenceId,
                                 @Valid @RequestBody UserRoleRequest request) {
        return moderationService.setRole(adminReferenceId, referenceId, request.role());
    }

    /** Rebuilds the Solr index from the database, e.g. after events went to a dead-letter topic. */
    @PostMapping("/search/reindex")
    public Map<String, Integer> reindex(@CurrentUser String adminReferenceId) {
        return Map.of("indexed", moderationService.reindex(adminReferenceId));
    }
}
