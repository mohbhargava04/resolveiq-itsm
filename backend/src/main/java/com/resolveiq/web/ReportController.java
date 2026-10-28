package com.resolveiq.web;

import com.resolveiq.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/** Management reports (team leads, administrators and managers). */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports & analytics")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    @Operation(summary = "Headline KPIs: volumes, SLA compliance, MTTA, MTTR, CSAT")
    public ReportService.Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.overview(from);
    }

    @GetMapping("/technician-workload")
    public List<ReportService.TechnicianLoad> workload() {
        return reports.technicianWorkload();
    }

    @GetMapping("/by-service")
    public List<ReportService.CountRow> byService(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.byService(from);
    }

    @GetMapping("/by-category")
    public List<ReportService.CountRow> byCategory(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.byCategory(from);
    }

    @GetMapping("/by-priority")
    public List<ReportService.CountRow> byPriority(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.byPriority(from);
    }

    @GetMapping("/by-status")
    public List<ReportService.CountRow> byStatus(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.byStatus(from);
    }

    @GetMapping("/trend")
    public List<ReportService.DayRow> trend(@RequestParam(defaultValue = "14") int days) {
        return reports.trend(days);
    }

    @GetMapping("/sla")
    public List<ReportService.SlaRow> sla(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return reports.slaPerformance(from);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @Operation(summary = "Download the incident register as CSV")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        byte[] body = reports.exportCsv(from).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"incidents.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(body);
    }
}
