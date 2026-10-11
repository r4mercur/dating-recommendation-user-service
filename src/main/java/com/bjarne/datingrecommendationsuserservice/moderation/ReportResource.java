package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.moderation.dto.ReportRequest;
import com.bjarne.datingrecommendationsuserservice.moderation.dto.ReportResponse;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/reports")
public class ReportResource {

    private final ReportService reportService;

    public ReportResource(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@CurrentUser String referenceId, @Valid @RequestBody ReportRequest request) {
        return reportService.report(referenceId, request);
    }
}
