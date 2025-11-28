package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.dtos.mappers.ItemMapper;
import com.mirai.inventoryservice.dtos.requests.ItemRequestDTO;
import com.mirai.inventoryservice.dtos.responses.ItemResponseDTO;
import com.mirai.inventoryservice.models.Item;
import com.mirai.inventoryservice.models.enums.ProductCategory;
import com.mirai.inventoryservice.services.ItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private final ItemService itemService;
    private final ItemMapper itemMapper;

    public ItemController(ItemService itemService, ItemMapper itemMapper) {
        this.itemService = itemService;
        this.itemMapper = itemMapper;
    }

    @PostMapping
    public ResponseEntity<ItemResponseDTO> createItem(@Valid @RequestBody ItemRequestDTO requestDTO) {
        Item item = itemService.createItem(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemMapper.toResponseDTO(item));
    }

    @GetMapping
    public ResponseEntity<Page<ItemResponseDTO>> listItems(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            Pageable pageable) {
        Page<Item> items = itemService.listItems(category, activeOnly, pageable);
        return ResponseEntity.ok(items.map(itemMapper::toResponseDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemResponseDTO> getItemById(@PathVariable UUID id) {
        Item item = itemService.getItemById(id);
        return ResponseEntity.ok(itemMapper.toResponseDTO(item));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ItemResponseDTO> getItemBySku(@PathVariable String sku) {
        Item item = itemService.getItemBySku(sku);
        return ResponseEntity.ok(itemMapper.toResponseDTO(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemResponseDTO> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequestDTO requestDTO) {
        Item item = itemService.updateItem(id, requestDTO);
        return ResponseEntity.ok(itemMapper.toResponseDTO(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateItem(@PathVariable UUID id) {
        itemService.deactivateItem(id);
        return ResponseEntity.noContent().build();
    }
}

