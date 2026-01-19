package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.BoxBinNotFoundException;
import com.mirai.inventoryservice.models.storage.BoxBin;
import com.mirai.inventoryservice.repositories.BoxBinRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BoxBinService {
    private final BoxBinRepository boxBinRepository;

    public BoxBinService(BoxBinRepository boxBinRepository) {
        this.boxBinRepository = boxBinRepository;
    }

    public BoxBin createBoxBin(String boxBinCode) {
        BoxBin boxBin = BoxBin.builder()
                .boxBinCode(boxBinCode)
                .build();
        return boxBinRepository.save(boxBin);
    }

    public BoxBin getBoxBinById(UUID id) {
        return boxBinRepository.findById(id)
                .orElseThrow(() -> new BoxBinNotFoundException("BoxBin not found with id: " + id));
    }

    public BoxBin getBoxBinByCode(String code) {
        return boxBinRepository.findByBoxBinCode(code)
                .orElseThrow(() -> new BoxBinNotFoundException("BoxBin not found with code: " + code));
    }

    public List<BoxBin> getAllBoxBins() {
        return boxBinRepository.findAll();
    }

    public BoxBin updateBoxBin(UUID id, String boxBinCode) {
        BoxBin boxBin = getBoxBinById(id);
        boxBin.setBoxBinCode(boxBinCode);
        return boxBinRepository.save(boxBin);
    }

    public void deleteBoxBin(UUID id) {
        BoxBin boxBin = getBoxBinById(id);
        boxBinRepository.delete(boxBin);
    }

    public boolean existsByCode(String code) {
        return boxBinRepository.existsByBoxBinCode(code);
    }
}

