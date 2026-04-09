package net.tecgurus.controller;

import net.tecgurus.model.DashboardSummary;
import net.tecgurus.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm:ss") LocalDateTime endDate,
            @RequestParam(required = false) Integer lowStockThreshold
    ) {
        DashboardSummary summary = dashboardService.getSummary(startDate, endDate, lowStockThreshold);
        return ResponseEntity.ok(summary);
    }
}
