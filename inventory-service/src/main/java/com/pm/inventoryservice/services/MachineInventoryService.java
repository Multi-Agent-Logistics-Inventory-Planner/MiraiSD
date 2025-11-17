package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.MachineInventoryMapper;
import com.pm.inventoryservice.dtos.requests.MachineInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.MachineInventoryResponseDTO;
import com.pm.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.pm.inventoryservice.exceptions.MachineInventoryNotFoundException;
import com.pm.inventoryservice.exceptions.MachineNotFoundException;
import com.pm.inventoryservice.models.Machine;
import com.pm.inventoryservice.models.MachineInventory;
import com.pm.inventoryservice.models.enums.ProductCategory;
import com.pm.inventoryservice.repositories.MachineInventoryRepository;
import com.pm.inventoryservice.repositories.MachineRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MachineInventoryService {

    private final MachineInventoryRepository machineInventoryRepository;
    private final MachineRepository machineRepository;

    public MachineInventoryService(
            MachineInventoryRepository machineInventoryRepository,
            MachineRepository machineRepository) {
        this.machineInventoryRepository = machineInventoryRepository;
        this.machineRepository = machineRepository;
    }

    @Transactional(readOnly = true)
    public List<MachineInventoryResponseDTO> getInventoryByMachine(@NonNull UUID machineId) {
        // Verify machine exists
        machineRepository.findById(machineId)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found with ID: " + machineId));

        return machineInventoryRepository.findByMachineId(machineId).stream()
                .map(MachineInventoryMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public MachineInventoryResponseDTO getInventoryItem(@NonNull UUID machineId, @NonNull UUID inventoryId) {
        MachineInventory inventory = machineInventoryRepository.findByIdAndMachineId(inventoryId, machineId)
                .orElseThrow(() -> new MachineInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in machine: " + machineId));

        return MachineInventoryMapper.toDTO(inventory);
    }

    @Transactional
    public MachineInventoryResponseDTO addInventory(@NonNull UUID machineId, @NonNull MachineInventoryRequestDTO request) {
        // Validate machine exists
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found with ID: " + machineId));

        // Validate category/subcategory rules
        validateCategorySubcategory(request.getCategory(), request.getSubcategory());

        MachineInventory inventory = MachineInventoryMapper.toEntity(request, machine);
        MachineInventory savedInventory = machineInventoryRepository.save(inventory);

        return MachineInventoryMapper.toDTO(savedInventory);
    }

    @Transactional
    public MachineInventoryResponseDTO updateInventory(
            @NonNull UUID machineId,
            @NonNull UUID inventoryId,
            @NonNull MachineInventoryRequestDTO request) {

        MachineInventory inventory = machineInventoryRepository.findByIdAndMachineId(inventoryId, machineId)
                .orElseThrow(() -> new MachineInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in machine: " + machineId));

        // Validate category/subcategory rules
        validateCategorySubcategory(request.getCategory(), request.getSubcategory());

        MachineInventoryMapper.updateEntityFromDTO(inventory, request);
        MachineInventory updatedInventory = machineInventoryRepository.save(inventory);

        return MachineInventoryMapper.toDTO(updatedInventory);
    }

    @Transactional
    public void deleteInventory(@NonNull UUID machineId, @NonNull UUID inventoryId) {
        MachineInventory inventory = machineInventoryRepository.findByIdAndMachineId(inventoryId, machineId)
                .orElseThrow(() -> new MachineInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in machine: " + machineId));

        machineInventoryRepository.delete(inventory);
    }

    private void validateCategorySubcategory(ProductCategory category, com.pm.inventoryservice.models.enums.ProductSubcategory subcategory) {
        if (category == ProductCategory.BLIND_BOX && subcategory == null) {
            throw new InvalidInventoryOperationException("Subcategory is required for Blind Box items");
        }
        if (category != ProductCategory.BLIND_BOX && subcategory != null) {
            throw new InvalidInventoryOperationException("Subcategory should only be set for Blind Box items");
        }
    }
}
