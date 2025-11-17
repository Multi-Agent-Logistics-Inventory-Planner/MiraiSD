package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.BinRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinResponseDTO;
import com.pm.inventoryservice.dtos.validators.CreateBinValidationGroup;
import com.pm.inventoryservice.services.BinService;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bins")
public class BinController {

    private final BinService binService;

    public BinController(BinService binService) {
        this.binService = binService;
    }

    @GetMapping
    public ResponseEntity<List<BinResponseDTO>> getAllBins() {
        List<BinResponseDTO> bins = binService.getAllBins();
        return ResponseEntity.ok().body(bins);
    }

    @PostMapping
    public ResponseEntity<BinResponseDTO> createBin(@Validated({Default.class, CreateBinValidationGroup.class})
                                                    @RequestBody @NonNull BinRequestDTO binRequestDTO) {
        BinResponseDTO responseDTO = binService.createBin(binRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BinResponseDTO> updateBin(@PathVariable @NonNull UUID id,
                                                    @Validated({Default.class}) @RequestBody @NonNull BinRequestDTO binRequestDTO) {
        BinResponseDTO responseDTO = binService.updateBin(id, binRequestDTO);
        return ResponseEntity.ok().body(responseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBin(@PathVariable @NonNull UUID id) {
        binService.deleteBin(id);
        return ResponseEntity.noContent().build();
    }
}


