package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.RackNotFoundException;
import com.mirai.inventoryservice.models.storage.Rack;
import com.mirai.inventoryservice.repositories.RackRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RackService {
    private final RackRepository rackRepository;

    public RackService(RackRepository rackRepository) {
        this.rackRepository = rackRepository;
    }

    public Rack createRack(String rackCode) {
        if (rackRepository.existsByRackCode(rackCode)) {
            throw new DuplicateLocationCodeException("Rack with code already exists: " + rackCode);
        }
        Rack rack = Rack.builder()
                .rackCode(rackCode)
                .build();
        return rackRepository.save(rack);
    }

    public Rack getRackById(UUID id) {
        return rackRepository.findById(id)
                .orElseThrow(() -> new RackNotFoundException("Rack not found with id: " + id));
    }

    public Rack getRackByCode(String code) {
        return rackRepository.findByRackCode(code)
                .orElseThrow(() -> new RackNotFoundException("Rack not found with code: " + code));
    }

    public List<Rack> getAllRacks() {
        return rackRepository.findAll();
    }

    public Rack updateRack(UUID id, String rackCode) {
        Rack rack = getRackById(id);
        if (!rackCode.equals(rack.getRackCode()) && rackRepository.existsByRackCode(rackCode)) {
            throw new DuplicateLocationCodeException("Rack with code already exists: " + rackCode);
        }
        rack.setRackCode(rackCode);
        return rackRepository.save(rack);
    }

    public void deleteRack(UUID id) {
        Rack rack = getRackById(id);
        rackRepository.delete(rack);
    }

    public boolean existsByCode(String code) {
        return rackRepository.existsByRackCode(code);
    }
}

