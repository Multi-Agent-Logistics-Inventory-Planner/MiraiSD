package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.FourCornerMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.FourCornerMachine;
import com.mirai.inventoryservice.repositories.FourCornerMachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FourCornerMachineServiceTest {

    @Mock
    private FourCornerMachineRepository fourCornerMachineRepository;

    @InjectMocks
    private FourCornerMachineService fourCornerMachineService;

    private FourCornerMachine testMachine;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testMachine = FourCornerMachine.builder()
                .id(testId)
                .fourCornerMachineCode("M1")
                .build();
    }

    @Nested
    @DisplayName("createFourCornerMachine")
    class CreateFourCornerMachineTests {

        @Test
        @DisplayName("should create machine with valid code")
        void shouldCreateMachineWithValidCode() {
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M1")).thenReturn(false);
            when(fourCornerMachineRepository.save(any(FourCornerMachine.class))).thenReturn(testMachine);

            FourCornerMachine result = fourCornerMachineService.createFourCornerMachine("M1");

            assertNotNull(result);
            assertEquals("M1", result.getFourCornerMachineCode());
            verify(fourCornerMachineRepository).save(any(FourCornerMachine.class));
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when code already exists")
        void shouldThrowExceptionWhenCodeExists() {
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M1")).thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    fourCornerMachineService.createFourCornerMachine("M1"));

            verify(fourCornerMachineRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getFourCornerMachineById")
    class GetFourCornerMachineByIdTests {

        @Test
        @DisplayName("should return machine when found")
        void shouldReturnMachineWhenFound() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));

            FourCornerMachine result = fourCornerMachineService.getFourCornerMachineById(testId);

            assertEquals(testMachine, result);
        }

        @Test
        @DisplayName("should throw FourCornerMachineNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.empty());

            assertThrows(FourCornerMachineNotFoundException.class, () ->
                    fourCornerMachineService.getFourCornerMachineById(testId));
        }
    }

    @Nested
    @DisplayName("getFourCornerMachineByCode")
    class GetFourCornerMachineByCodeTests {

        @Test
        @DisplayName("should return machine when found by code")
        void shouldReturnMachineWhenFoundByCode() {
            when(fourCornerMachineRepository.findByFourCornerMachineCode("M1")).thenReturn(Optional.of(testMachine));

            FourCornerMachine result = fourCornerMachineService.getFourCornerMachineByCode("M1");

            assertEquals(testMachine, result);
        }

        @Test
        @DisplayName("should throw FourCornerMachineNotFoundException when code not found")
        void shouldThrowExceptionWhenCodeNotFound() {
            when(fourCornerMachineRepository.findByFourCornerMachineCode("M99")).thenReturn(Optional.empty());

            assertThrows(FourCornerMachineNotFoundException.class, () ->
                    fourCornerMachineService.getFourCornerMachineByCode("M99"));
        }
    }

    @Nested
    @DisplayName("getAllFourCornerMachines")
    class GetAllFourCornerMachinesTests {

        @Test
        @DisplayName("should return all machines")
        void shouldReturnAllMachines() {
            FourCornerMachine machine2 = FourCornerMachine.builder()
                    .id(UUID.randomUUID())
                    .fourCornerMachineCode("M2")
                    .build();
            when(fourCornerMachineRepository.findAll()).thenReturn(List.of(testMachine, machine2));

            List<FourCornerMachine> result = fourCornerMachineService.getAllFourCornerMachines();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no machines exist")
        void shouldReturnEmptyListWhenNoMachines() {
            when(fourCornerMachineRepository.findAll()).thenReturn(List.of());

            List<FourCornerMachine> result = fourCornerMachineService.getAllFourCornerMachines();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateFourCornerMachine")
    class UpdateFourCornerMachineTests {

        @Test
        @DisplayName("should update machine code successfully")
        void shouldUpdateMachineCodeSuccessfully() {
            FourCornerMachine updatedMachine = FourCornerMachine.builder()
                    .id(testId)
                    .fourCornerMachineCode("M2")
                    .build();

            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M2")).thenReturn(false);
            when(fourCornerMachineRepository.save(any(FourCornerMachine.class))).thenReturn(updatedMachine);

            FourCornerMachine result = fourCornerMachineService.updateFourCornerMachine(testId, "M2");

            assertEquals("M2", result.getFourCornerMachineCode());
            verify(fourCornerMachineRepository).save(any(FourCornerMachine.class));
        }

        @Test
        @DisplayName("should allow updating to same code")
        void shouldAllowUpdatingToSameCode() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(fourCornerMachineRepository.save(any(FourCornerMachine.class))).thenReturn(testMachine);

            FourCornerMachine result = fourCornerMachineService.updateFourCornerMachine(testId, "M1");

            assertEquals("M1", result.getFourCornerMachineCode());
            verify(fourCornerMachineRepository, never()).existsByFourCornerMachineCode(any());
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when updating to existing code")
        void shouldThrowExceptionWhenUpdatingToExistingCode() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M2")).thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    fourCornerMachineService.updateFourCornerMachine(testId, "M2"));

            verify(fourCornerMachineRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteFourCornerMachine")
    class DeleteFourCornerMachineTests {

        @Test
        @DisplayName("should delete machine when found")
        void shouldDeleteMachineWhenFound() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));

            fourCornerMachineService.deleteFourCornerMachine(testId);

            verify(fourCornerMachineRepository).delete(testMachine);
        }

        @Test
        @DisplayName("should throw FourCornerMachineNotFoundException when deleting non-existent machine")
        void shouldThrowExceptionWhenDeletingNonExistent() {
            when(fourCornerMachineRepository.findById(testId)).thenReturn(Optional.empty());

            assertThrows(FourCornerMachineNotFoundException.class, () ->
                    fourCornerMachineService.deleteFourCornerMachine(testId));

            verify(fourCornerMachineRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCodeTests {

        @Test
        @DisplayName("should return true when code exists")
        void shouldReturnTrueWhenCodeExists() {
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M1")).thenReturn(true);

            assertTrue(fourCornerMachineService.existsByCode("M1"));
        }

        @Test
        @DisplayName("should return false when code does not exist")
        void shouldReturnFalseWhenCodeDoesNotExist() {
            when(fourCornerMachineRepository.existsByFourCornerMachineCode("M99")).thenReturn(false);

            assertFalse(fourCornerMachineService.existsByCode("M99"));
        }
    }
}
