package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.responses.ForecastPredictionResponseDTO;
import com.mirai.inventoryservice.services.ForecastService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forecasts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping
    public ResponseEntity<Page<ForecastPredictionResponseDTO>> getAllForecasts(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(forecastService.getAllForecasts(pageable));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ForecastPredictionResponseDTO> getForecastByItem(@PathVariable UUID itemId) {
        ForecastPredictionResponseDTO forecast = forecastService.getForecastByItem(itemId);
        if (forecast == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(forecast);
    }

    @GetMapping("/at-risk")
    public ResponseEntity<List<ForecastPredictionResponseDTO>> getAtRiskItems(
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int daysThreshold) {
        return ResponseEntity.ok(forecastService.getAtRiskForecasts(daysThreshold));
    }
}
