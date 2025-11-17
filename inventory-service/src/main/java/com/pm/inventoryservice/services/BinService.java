package com.pm.inventoryservice.services;

import com.pm.inventoryservice.dtos.mappers.BinMapper;
import com.pm.inventoryservice.dtos.requests.BinRequestDTO;
import com.pm.inventoryservice.dtos.responses.BinResponseDTO;
import com.pm.inventoryservice.exceptions.BinCodeAlreadyExistsException;
import com.pm.inventoryservice.exceptions.BinNotFoundException;
import com.pm.inventoryservice.models.Bin;
import com.pm.inventoryservice.repositories.BinRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BinService {

    private final BinRepository binRepository;

    public BinService(BinRepository binRepository) {
        this.binRepository = binRepository;
    }

    @Transactional(readOnly = true)
    public List<BinResponseDTO> getAllBins() {
        List<Bin> bins = binRepository.findAll();
        return bins.stream()
                .map(BinMapper::toDTO)
                .toList();
    }

    @Transactional
    public BinResponseDTO createBin(@NonNull BinRequestDTO binRequestDTO) {
        if (binRepository.existsByBinCode(binRequestDTO.getBinCode())) {
            throw new BinCodeAlreadyExistsException(
                    "A bin with this code already exists: " + binRequestDTO.getBinCode());
        }
        Bin newBin = binRepository.save(BinMapper.toEntity(binRequestDTO));
        return BinMapper.toDTO(newBin);
    }

    @Transactional
    public BinResponseDTO updateBin(@NonNull UUID id, @NonNull BinRequestDTO binRequestDTO) {
        Bin bin = binRepository.findById(id)
                .orElseThrow(() -> new BinNotFoundException("Bin not found with ID: " + id));

        if (binRepository.existsByBinCodeAndIdNot(binRequestDTO.getBinCode(), id)) {
            throw new BinCodeAlreadyExistsException(
                    "A bin with this code already exists: " + binRequestDTO.getBinCode());
        }

        bin.setBinCode(binRequestDTO.getBinCode());
        Bin updatedBin = binRepository.save(bin);
        return BinMapper.toDTO(updatedBin);
    }

    @Transactional
    public void deleteBin(@NonNull UUID id) {
        binRepository.findById(id)
                .orElseThrow(() -> new BinNotFoundException("Bin not found with ID: " + id));
        binRepository.deleteById(id);
    }
}


