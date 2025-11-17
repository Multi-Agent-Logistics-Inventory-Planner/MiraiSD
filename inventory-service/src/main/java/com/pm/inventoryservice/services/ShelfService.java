package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.ShelfMapper;
import com.pm.inventoryservice.dtos.requests.ShelfRequestDTO;
import com.pm.inventoryservice.dtos.responses.ShelfResponseDTO;
import com.pm.inventoryservice.exceptions.ShelfCodeAlreadyExistsException;
import com.pm.inventoryservice.exceptions.ShelfNotFoundException;
import com.pm.inventoryservice.models.Shelf;
import com.pm.inventoryservice.repositories.ShelfRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShelfService {

    private final ShelfRepository shelfRepository;

    public ShelfService(ShelfRepository shelfRepository) {
        this.shelfRepository = shelfRepository;
    }

    @Transactional(readOnly = true)
    public List<ShelfResponseDTO> getAllShelves() {
        List<Shelf> shelves = shelfRepository.findAll();
        return shelves.stream()
                .map(ShelfMapper::toDTO)
                .toList();
    }

    @Transactional
    public ShelfResponseDTO createShelf(@NonNull ShelfRequestDTO shelfRequestDTO) {
        if (shelfRepository.existsByShelfCode(shelfRequestDTO.getShelfCode())) {
            throw new ShelfCodeAlreadyExistsException(
                    "A shelf with this code already exists: " + shelfRequestDTO.getShelfCode());
        }
        Shelf newShelf = shelfRepository.save(ShelfMapper.toEntity(shelfRequestDTO));
        return ShelfMapper.toDTO(newShelf);
    }

    @Transactional
    public ShelfResponseDTO updateShelf(@NonNull UUID id, @NonNull ShelfRequestDTO shelfRequestDTO) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new ShelfNotFoundException("Shelf not found with ID: " + id));

        if (shelfRepository.existsByShelfCodeAndIdNot(shelfRequestDTO.getShelfCode(), id)) {
            throw new ShelfCodeAlreadyExistsException(
                    "A shelf with this code already exists: " + shelfRequestDTO.getShelfCode());
        }

        shelf.setShelfCode(shelfRequestDTO.getShelfCode());
        Shelf updatedShelf = shelfRepository.save(shelf);
        return ShelfMapper.toDTO(updatedShelf);
    }

    @Transactional
    public void deleteShelf(@NonNull UUID id) {
        shelfRepository.findById(id)
                .orElseThrow(() -> new ShelfNotFoundException("Shelf not found with ID: " + id));
        shelfRepository.deleteById(id);
    }
}


