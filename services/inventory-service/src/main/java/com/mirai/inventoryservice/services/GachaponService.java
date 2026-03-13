package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.GachaponNotFoundException;
import com.mirai.inventoryservice.models.storage.Gachapon;
import com.mirai.inventoryservice.repositories.GachaponRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GachaponService {
    private final GachaponRepository gachaponRepository;

    public GachaponService(GachaponRepository gachaponRepository) {
        this.gachaponRepository = gachaponRepository;
    }

    public Gachapon createGachapon(String gachaponCode) {
        if (gachaponRepository.existsByGachaponCode(gachaponCode)) {
            throw new DuplicateLocationCodeException("Gachapon with code already exists: " + gachaponCode);
        }
        Gachapon gachapon = Gachapon.builder()
                .gachaponCode(gachaponCode)
                .build();
        return gachaponRepository.save(gachapon);
    }

    public Gachapon getGachaponById(UUID id) {
        return gachaponRepository.findById(id)
                .orElseThrow(() -> new GachaponNotFoundException("Gachapon not found with id: " + id));
    }

    public Gachapon getGachaponByCode(String code) {
        return gachaponRepository.findByGachaponCode(code)
                .orElseThrow(() -> new GachaponNotFoundException("Gachapon not found with code: " + code));
    }

    public List<Gachapon> getAllGachapons() {
        return gachaponRepository.findAll();
    }

    public Gachapon updateGachapon(UUID id, String gachaponCode) {
        Gachapon gachapon = getGachaponById(id);
        if (!gachaponCode.equals(gachapon.getGachaponCode()) && gachaponRepository.existsByGachaponCode(gachaponCode)) {
            throw new DuplicateLocationCodeException("Gachapon with code already exists: " + gachaponCode);
        }
        gachapon.setGachaponCode(gachaponCode);
        return gachaponRepository.save(gachapon);
    }

    public void deleteGachapon(UUID id) {
        Gachapon gachapon = getGachaponById(id);
        gachaponRepository.delete(gachapon);
    }

    public boolean existsByCode(String code) {
        return gachaponRepository.existsByGachaponCode(code);
    }
}
