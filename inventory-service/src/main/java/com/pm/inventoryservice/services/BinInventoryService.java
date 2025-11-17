package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.BinInventoryMapper;
import com.pm.inventoryservice.dtos.requests.BinInventoryRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinInventoryResponseDTO;
import com.pm.inventoryservice.exceptions.BinInventoryNotFoundException;
import com.pm.inventoryservice.exceptions.BinNotFoundException;
import com.pm.inventoryservice.exceptions.InvalidInventoryOperationException;
import com.pm.inventoryservice.models.Bin;
import com.pm.inventoryservice.models.BinInventory;
import com.pm.inventoryservice.models.enums.ProductCategory;
import com.pm.inventoryservice.repositories.BinInventoryRepository;
import com.pm.inventoryservice.repositories.BinRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BinInventoryService {

    private final BinInventoryRepository binInventoryRepository;
    private final BinRepository binRepository;

    public BinInventoryService(
            BinInventoryRepository binInventoryRepository,
            BinRepository binRepository) {
        this.binInventoryRepository = binInventoryRepository;
        this.binRepository = binRepository;
    }

    @Transactional(readOnly = true)
    public List<BinInventoryResponseDTO> getInventoryByBin(@NonNull UUID binId) {
        // Verify bin exists
        binRepository.findById(binId)
                .orElseThrow(() -> new BinNotFoundException("Bin not found with ID: " + binId));

        return binInventoryRepository.findByBinId(binId).stream()
                .map(BinInventoryMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BinInventoryResponseDTO getInventoryItem(@NonNull UUID binId, @NonNull UUID inventoryId) {
        BinInventory inventory = binInventoryRepository.findByIdAndBinId(inventoryId, binId)
                .orElseThrow(() -> new BinInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in bin: " + binId));

        return BinInventoryMapper.toDTO(inventory);
    }

    @Transactional
    public BinInventoryResponseDTO addInventory(@NonNull UUID binId, @NonNull BinInventoryRequestDTO request) {
        // Validate bin exists
        Bin bin = binRepository.findById(binId)
                .orElseThrow(() -> new BinNotFoundException("Bin not found with ID: " + binId));

        // Validate category (bins can only hold PLUSHIE or KEYCHAIN)
        validateBinCategory(request.getCategory());

        BinInventory inventory = BinInventoryMapper.toEntity(request, bin);
        BinInventory savedInventory = binInventoryRepository.save(inventory);

        return BinInventoryMapper.toDTO(savedInventory);
    }

    @Transactional
    public BinInventoryResponseDTO updateInventory(
            @NonNull UUID binId,
            @NonNull UUID inventoryId,
            @NonNull BinInventoryRequestDTO request) {

        BinInventory inventory = binInventoryRepository.findByIdAndBinId(inventoryId, binId)
                .orElseThrow(() -> new BinInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in bin: " + binId));

        // Validate category (bins can only hold PLUSHIE or KEYCHAIN)
        validateBinCategory(request.getCategory());

        BinInventoryMapper.updateEntityFromDTO(inventory, request);
        BinInventory updatedInventory = binInventoryRepository.save(inventory);

        return BinInventoryMapper.toDTO(updatedInventory);
    }

    @Transactional
    public void deleteInventory(@NonNull UUID binId, @NonNull UUID inventoryId) {
        BinInventory inventory = binInventoryRepository.findByIdAndBinId(inventoryId, binId)
                .orElseThrow(() -> new BinInventoryNotFoundException(
                        "Inventory item not found with ID: " + inventoryId + " in bin: " + binId));

        binInventoryRepository.delete(inventory);
    }

    private void validateBinCategory(ProductCategory category) {
        if (category != ProductCategory.PLUSHIE && category != ProductCategory.KEYCHAIN) {
            throw new InvalidInventoryOperationException("Bins can only contain Plushie or Keychain items");
        }
    }
}

