package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.WindowNotFoundException;
import com.mirai.inventoryservice.models.storage.Window;
import com.mirai.inventoryservice.repositories.WindowRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WindowService {
    private final WindowRepository windowRepository;

    public WindowService(WindowRepository windowRepository) {
        this.windowRepository = windowRepository;
    }

    public Window createWindow(String windowCode) {
        if (windowRepository.existsByWindowCode(windowCode)) {
            throw new DuplicateLocationCodeException("Window with code already exists: " + windowCode);
        }
        Window window = Window.builder()
                .windowCode(windowCode)
                .build();
        return windowRepository.save(window);
    }

    public Window getWindowById(UUID id) {
        return windowRepository.findById(id)
                .orElseThrow(() -> new WindowNotFoundException("Window not found with id: " + id));
    }

    public Window getWindowByCode(String code) {
        return windowRepository.findByWindowCode(code)
                .orElseThrow(() -> new WindowNotFoundException("Window not found with code: " + code));
    }

    public List<Window> getAllWindows() {
        return windowRepository.findAll();
    }

    public Window updateWindow(UUID id, String windowCode) {
        Window window = getWindowById(id);
        if (!windowCode.equals(window.getWindowCode()) && windowRepository.existsByWindowCode(windowCode)) {
            throw new DuplicateLocationCodeException("Window with code already exists: " + windowCode);
        }
        window.setWindowCode(windowCode);
        return windowRepository.save(window);
    }

    public void deleteWindow(UUID id) {
        Window window = getWindowById(id);
        windowRepository.delete(window);
    }

    public boolean existsByCode(String code) {
        return windowRepository.existsByWindowCode(code);
    }
}

