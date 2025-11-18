package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.CabinetNotFoundException;
import com.mirai.inventoryservice.models.storage.Cabinet;
import com.mirai.inventoryservice.repositories.CabinetRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CabinetService {
    private final CabinetRepository cabinetRepository;

    public CabinetService(CabinetRepository cabinetRepository) {
        this.cabinetRepository = cabinetRepository;
    }

    public Cabinet createCabinet(String cabinetCode) {
        Cabinet cabinet = Cabinet.builder()
                .cabinetCode(cabinetCode)
                .build();
        return cabinetRepository.save(cabinet);
    }

    public Cabinet getCabinetById(UUID id) {
        return cabinetRepository.findById(id)
                .orElseThrow(() -> new CabinetNotFoundException("Cabinet not found with id: " + id));
    }

    public Cabinet getCabinetByCode(String code) {
        return cabinetRepository.findByCabinetCode(code)
                .orElseThrow(() -> new CabinetNotFoundException("Cabinet not found with code: " + code));
    }

    public List<Cabinet> getAllCabinets() {
        return cabinetRepository.findAll();
    }

    public Cabinet updateCabinet(UUID id, String cabinetCode) {
        Cabinet cabinet = getCabinetById(id);
        cabinet.setCabinetCode(cabinetCode);
        return cabinetRepository.save(cabinet);
    }

    public void deleteCabinet(UUID id) {
        Cabinet cabinet = getCabinetById(id);
        cabinetRepository.delete(cabinet);
    }

    public boolean existsByCode(String code) {
        return cabinetRepository.existsByCabinetCode(code);
    }
}

