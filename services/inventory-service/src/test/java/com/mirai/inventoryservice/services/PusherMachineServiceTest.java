package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.exceptions.DuplicateLocationCodeException;
import com.mirai.inventoryservice.exceptions.PusherMachineNotFoundException;
import com.mirai.inventoryservice.models.storage.PusherMachine;
import com.mirai.inventoryservice.repositories.PusherMachineRepository;
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
class PusherMachineServiceTest {

    @Mock
    private PusherMachineRepository pusherMachineRepository;

    @InjectMocks
    private PusherMachineService pusherMachineService;

    private PusherMachine testMachine;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testMachine = PusherMachine.builder()
                .id(testId)
                .pusherMachineCode("P1")
                .build();
    }

    @Nested
    @DisplayName("createPusherMachine")
    class CreatePusherMachineTests {

        @Test
        @DisplayName("should create machine with valid code")
        void shouldCreateMachineWithValidCode() {
            when(pusherMachineRepository.existsByPusherMachineCode("P1")).thenReturn(false);
            when(pusherMachineRepository.save(any(PusherMachine.class))).thenReturn(testMachine);

            PusherMachine result = pusherMachineService.createPusherMachine("P1");

            assertNotNull(result);
            assertEquals("P1", result.getPusherMachineCode());
            verify(pusherMachineRepository).save(any(PusherMachine.class));
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when code already exists")
        void shouldThrowExceptionWhenCodeExists() {
            when(pusherMachineRepository.existsByPusherMachineCode("P1")).thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    pusherMachineService.createPusherMachine("P1"));

            verify(pusherMachineRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getPusherMachineById")
    class GetPusherMachineByIdTests {

        @Test
        @DisplayName("should return machine when found")
        void shouldReturnMachineWhenFound() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));

            PusherMachine result = pusherMachineService.getPusherMachineById(testId);

            assertEquals(testMachine, result);
        }

        @Test
        @DisplayName("should throw PusherMachineNotFoundException when not found")
        void shouldThrowExceptionWhenNotFound() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.empty());

            assertThrows(PusherMachineNotFoundException.class, () ->
                    pusherMachineService.getPusherMachineById(testId));
        }
    }

    @Nested
    @DisplayName("getPusherMachineByCode")
    class GetPusherMachineByCodeTests {

        @Test
        @DisplayName("should return machine when found by code")
        void shouldReturnMachineWhenFoundByCode() {
            when(pusherMachineRepository.findByPusherMachineCode("P1")).thenReturn(Optional.of(testMachine));

            PusherMachine result = pusherMachineService.getPusherMachineByCode("P1");

            assertEquals(testMachine, result);
        }

        @Test
        @DisplayName("should throw PusherMachineNotFoundException when code not found")
        void shouldThrowExceptionWhenCodeNotFound() {
            when(pusherMachineRepository.findByPusherMachineCode("P99")).thenReturn(Optional.empty());

            assertThrows(PusherMachineNotFoundException.class, () ->
                    pusherMachineService.getPusherMachineByCode("P99"));
        }
    }

    @Nested
    @DisplayName("getAllPusherMachines")
    class GetAllPusherMachinesTests {

        @Test
        @DisplayName("should return all machines")
        void shouldReturnAllMachines() {
            PusherMachine machine2 = PusherMachine.builder()
                    .id(UUID.randomUUID())
                    .pusherMachineCode("P2")
                    .build();
            when(pusherMachineRepository.findAll()).thenReturn(List.of(testMachine, machine2));

            List<PusherMachine> result = pusherMachineService.getAllPusherMachines();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no machines exist")
        void shouldReturnEmptyListWhenNoMachines() {
            when(pusherMachineRepository.findAll()).thenReturn(List.of());

            List<PusherMachine> result = pusherMachineService.getAllPusherMachines();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updatePusherMachine")
    class UpdatePusherMachineTests {

        @Test
        @DisplayName("should update machine code successfully")
        void shouldUpdateMachineCodeSuccessfully() {
            PusherMachine updatedMachine = PusherMachine.builder()
                    .id(testId)
                    .pusherMachineCode("P2")
                    .build();

            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(pusherMachineRepository.existsByPusherMachineCode("P2")).thenReturn(false);
            when(pusherMachineRepository.save(any(PusherMachine.class))).thenReturn(updatedMachine);

            PusherMachine result = pusherMachineService.updatePusherMachine(testId, "P2");

            assertEquals("P2", result.getPusherMachineCode());
            verify(pusherMachineRepository).save(any(PusherMachine.class));
        }

        @Test
        @DisplayName("should allow updating to same code")
        void shouldAllowUpdatingToSameCode() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(pusherMachineRepository.save(any(PusherMachine.class))).thenReturn(testMachine);

            PusherMachine result = pusherMachineService.updatePusherMachine(testId, "P1");

            assertEquals("P1", result.getPusherMachineCode());
            verify(pusherMachineRepository, never()).existsByPusherMachineCode(any());
        }

        @Test
        @DisplayName("should throw DuplicateLocationCodeException when updating to existing code")
        void shouldThrowExceptionWhenUpdatingToExistingCode() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));
            when(pusherMachineRepository.existsByPusherMachineCode("P2")).thenReturn(true);

            assertThrows(DuplicateLocationCodeException.class, () ->
                    pusherMachineService.updatePusherMachine(testId, "P2"));

            verify(pusherMachineRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deletePusherMachine")
    class DeletePusherMachineTests {

        @Test
        @DisplayName("should delete machine when found")
        void shouldDeleteMachineWhenFound() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.of(testMachine));

            pusherMachineService.deletePusherMachine(testId);

            verify(pusherMachineRepository).delete(testMachine);
        }

        @Test
        @DisplayName("should throw PusherMachineNotFoundException when deleting non-existent machine")
        void shouldThrowExceptionWhenDeletingNonExistent() {
            when(pusherMachineRepository.findById(testId)).thenReturn(Optional.empty());

            assertThrows(PusherMachineNotFoundException.class, () ->
                    pusherMachineService.deletePusherMachine(testId));

            verify(pusherMachineRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCodeTests {

        @Test
        @DisplayName("should return true when code exists")
        void shouldReturnTrueWhenCodeExists() {
            when(pusherMachineRepository.existsByPusherMachineCode("P1")).thenReturn(true);

            assertTrue(pusherMachineService.existsByCode("P1"));
        }

        @Test
        @DisplayName("should return false when code does not exist")
        void shouldReturnFalseWhenCodeDoesNotExist() {
            when(pusherMachineRepository.existsByPusherMachineCode("P99")).thenReturn(false);

            assertFalse(pusherMachineService.existsByCode("P99"));
        }
    }
}
