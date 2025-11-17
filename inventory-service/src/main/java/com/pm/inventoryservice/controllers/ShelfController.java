package com.pm.inventoryservice.controllers;

import com.pm.inventoryservice.dtos.requests.ShelfRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfResponseDTO;
import com.pm.inventoryservice.dtos.validators.CreateShelfValidationGroup;
import com.pm.inventoryservice.services.ShelfService;
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
@RequestMapping("/shelves")
public class ShelfController {

    private final ShelfService shelfService;

    public ShelfController(ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    @GetMapping
    public ResponseEntity<List<ShelfResponseDTO>> getAllShelves() {
        List<ShelfResponseDTO> shelves = shelfService.getAllShelves();
        return ResponseEntity.ok().body(shelves);
    }

    @PostMapping
    public ResponseEntity<ShelfResponseDTO> createShelf(@Validated({Default.class, CreateShelfValidationGroup.class})
                                                        @RequestBody @NonNull ShelfRequestDTO shelfRequestDTO) {
        ShelfResponseDTO responseDTO = shelfService.createShelf(shelfRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShelfResponseDTO> updateShelf(@PathVariable @NonNull UUID id,
                                                        @Validated({Default.class}) @RequestBody @NonNull ShelfRequestDTO shelfRequestDTO) {
        ShelfResponseDTO responseDTO = shelfService.updateShelf(id, shelfRequestDTO);
        return ResponseEntity.ok().body(responseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShelf(@PathVariable @NonNull UUID id) {
        shelfService.deleteShelf(id);
        return ResponseEntity.noContent().build();
    }
}


