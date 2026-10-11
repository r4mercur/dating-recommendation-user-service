package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.ConflictException;
import com.bjarne.datingrecommendationsuserservice.interaction.BlockService;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ReportRequest;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ReportResponse;
import com.bjarne.datingrecommendationsuserservice.security.RateLimiter;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final RateLimiter rateLimiter;

    public ReportService(ReportRepository reportRepository, UserService userService, BlockService blockService,
                         RateLimiter rateLimiter) {
        this.reportRepository = reportRepository;
        this.userService = userService;
        this.blockService = blockService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Reports a user for moderation and optionally blocks them. One open report per pair; users can be reported
     * in any status, e.g. after they deactivated their profile.
     */
    @Transactional
    public ReportResponse report(String referenceId, ReportRequest request) {
        rateLimiter.check(RateLimiter.REPORTS, referenceId);
        if (referenceId.equals(request.referenceId())) {
            throw new BadRequestException("Users cannot report themselves");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        if (request.reason() == ReportReason.OTHER && comment == null) {
            throw new BadRequestException("Reason OTHER needs a comment");
        }

        User reporter = userService.getByReferenceId(referenceId);
        User reported = userService.getByReferenceId(request.referenceId());
        if (reportRepository.existsByReporterIdAndReportedIdAndStatus(reporter.getId(), reported.getId(), ReportStatus.OPEN)) {
            throw new ConflictException("You already reported this user");
        }

        Report report = reportRepository.save(new Report(reporter, reported, request.reason(), comment));
        if (request.shouldBlock()) {
            blockService.block(referenceId, request.referenceId());
        }

        LOGGER.info("User {} reported {} for {}", referenceId, request.referenceId(), request.reason());
        return ReportResponse.from(report, request.shouldBlock());
    }
}
