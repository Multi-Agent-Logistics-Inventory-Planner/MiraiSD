package com.mirai.inventoryservice.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootboxServiceTest {

    @Test
    @DisplayName("toFirstNameLastInitial keeps last name as a single initial")
    void formatsMultiWordName() {
        assertEquals("John D.", LootboxService.toFirstNameLastInitial("John Doe"));
        assertEquals("Mary W.", LootboxService.toFirstNameLastInitial("Mary Jane Watson"));
        assertEquals("Eric L.", LootboxService.toFirstNameLastInitial("  Eric   Liu  "));
    }

    @Test
    @DisplayName("Single-word names render as-is so we don't show '.'")
    void singleWordNameStaysIntact() {
        assertEquals("Cher", LootboxService.toFirstNameLastInitial("Cher"));
    }

    @Test
    @DisplayName("Null or blank names fall back to 'Someone'")
    void blankNamesFallBack() {
        assertEquals("Someone", LootboxService.toFirstNameLastInitial(null));
        assertEquals("Someone", LootboxService.toFirstNameLastInitial(""));
        assertEquals("Someone", LootboxService.toFirstNameLastInitial("   "));
    }

    @Test
    @DisplayName("Last-name initial is uppercased even when input is lower-cased")
    void lastInitialUppercased() {
        assertEquals("Anna b.".replace("b", "B"), LootboxService.toFirstNameLastInitial("Anna brown"));
    }
}
