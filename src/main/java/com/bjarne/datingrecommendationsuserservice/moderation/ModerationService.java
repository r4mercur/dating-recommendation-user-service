package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.ConflictException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.AdminReportResponse;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ModeratedUser;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ResolveReportRequest;
import com.bjarne.datingrecommendationsuserservice.search.SearchIndexService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actions of administrators. Every action is logged with the acting admin.
 */
@Service
public class ModerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModerationService.class);
    /** Deletion has its own flow with a grace period (DELETE /api/me), admins cannot shortcut it. */
    private static final Set<UserStatus> ASSIGNABLE_STATUSES = Set.of(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.BLOCKED);

    private final ReportRepository reportRepository;
    private final UserService userService;
    private final SearchIndexService searchIndexService;
    private final ProfileMapper profileMapper;

    public ModerationService(ReportRepository reportRepository,
                             UserService userService,
                             SearchIndexService searchIndexService,
                             ProfileMapper profileMapper) {
        this.reportRepository = reportRepository;
        this.userService = userService;
        this.searchIndexService = searchIndexService;
        this.profileMapper = profileMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminReportResponse> getReports(ReportStatus status) {
        List<Report> reports = reportRepository.findQueue(status);
        Map<Long, Long> openReports = reports.isEmpty() ? Map.of()
                : reportRepository.countByReported(reports.stream().map(report -> report.getReported().getId()).distinct().toList(),
                        ReportStatus.OPEN).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return reports.stream()
                .map(report -> toResponse(report, openReports.getOrDefault(report.getReported().getId(), 0L)))
                .toList();
    }

    /**
     * DISMISS closes only this report. BLOCK_USER blocks the reported user and closes all their open reports.
     */
    @Transactional
    public AdminReportResponse resolve(String adminReferenceId, String reportReferenceId, ResolveReportRequest request) {
        User admin = userService.getByReferenceId(adminReferenceId);
        Report report = reportRepository.findByReferenceId(reportReferenceId)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportReferenceId));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("Report is already closed");
        }

        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (request.action() == ResolveReportRequest.Action.DISMISS) {
            report.close(ReportStatus.DISMISSED, admin, note);
        } else {
            User reported = report.getReported();
            userService.changeStatus(reported.getReferenceId(), UserStatus.BLOCKED);
            reportRepository.findAllByReportedIdAndStatus(reported.getId(), ReportStatus.OPEN)
                    .forEach(open -> open.close(ReportStatus.RESOLVED, admin, note));
        }

        LOGGER.info("Admin {} closed report {} with {}", adminReferenceId, reportReferenceId, request.action());
        return toResponse(report, 0);
    }

    @Transactional
    public ModeratedUser setStatus(String adminReferenceId, String referenceId, UserStatus status) {
        if (!ASSIGNABLE_STATUSES.contains(status)) {
            throw new BadRequestException("Status " + status + " cannot be set by an admin");
        }
        if (adminReferenceId.equals(referenceId)) {
            throw new BadRequestException("Admins cannot change their own status");
        }

        User user = userService.changeStatus(referenceId, status);
        LOGGER.info("Admin {} set status of {} to {}", adminReferenceId, referenceId, status);
        return toModeratedUser(user, openReports(user));
    }

    /**
     * Role changes take effect with the next login, because the role is part of the token.
     */
    @Transactional
    public ModeratedUser setRole(String adminReferenceId, String referenceId, Role role) {
        if (adminReferenceId.equals(referenceId)) {
            throw new BadRequestException("Admins cannot change their own role");
        }

        User user = userService.setRole(referenceId, role);
        LOGGER.info("Admin {} set role of {} to {}", adminReferenceId, referenceId, role);
        return toModeratedUser(user, openReports(user));
    }

    public int reindex(String adminReferenceId) {
        LOGGER.info("Admin {} started a full reindex", adminReferenceId);
        return searchIndexService.reindexAll();
    }

    private long openReports(User user) {
        return reportRepository.findAllByReportedIdAndStatus(user.getId(), ReportStatus.OPEN).size();
    }

    private AdminReportResponse toResponse(Report report, long openReports) {
        return new AdminReportResponse(
                report.getReferenceId(),
                report.getReason(),
                report.getComment(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReporter() == null ? null : report.getReporter().getReferenceId(),
                toModeratedUser(report.getReported(), openReports),
                report.getResolvedAt(),
                report.getResolvedBy() == null ? null : report.getResolvedBy().getReferenceId(),
                report.getResolutionNote());
    }

    private ModeratedUser toModeratedUser(User user, long openReports) {
        return new ModeratedUser(profileMapper.toPublicProfile(user), user.getEmail(), user.getStatus(), user.getRole(), openReports);
    }
}
