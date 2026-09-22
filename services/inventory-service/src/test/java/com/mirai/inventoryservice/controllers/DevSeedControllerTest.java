package com.mirai.inventoryservice.controllers;

import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.identity.application.MembershipAuthorizer;
import com.mirai.inventoryservice.identity.domain.User;
import com.mirai.inventoryservice.identity.domain.UserRole;
import com.mirai.inventoryservice.identity.infrastructure.UserRepository;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.domain.StorageLocation;
import com.mirai.inventoryservice.sites.infrastructure.LocationRepository;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import com.mirai.inventoryservice.sites.infrastructure.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevSeedControllerTest {
    private static final String ADMIN_EMAIL = "jjmatu16@yahoo.com";
    private static final String EMPLOYEE_EMAIL = "mjpark019@gmail.com";
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock MembershipAuthorizer membershipAuthorizer;
    @Mock SiteRepository siteRepository;
    @Mock StorageLocationRepository storageLocationRepository;
    @Mock LocationRepository locationRepository;
    @InjectMocks DevSeedController controller;
    private final Map<String, User> users = new HashMap<>();

    @BeforeEach
    void existingInventory() {
        Site site = Site.builder().id(UUID.randomUUID()).code("MAIN").build();
        StorageLocation storage = StorageLocation.builder().id(UUID.randomUUID()).site(site).build();
        when(siteRepository.findByCode("MAIN")).thenReturn(Optional.of(site));
        when(storageLocationRepository.findByCodeAndSite_Code(anyString(), eq("MAIN")))
            .thenReturn(Optional.of(storage));
        when(locationRepository.findByLocationCodeAndStorageLocation_Id(eq("NA"), any()))
            .thenReturn(Optional.of(Location.builder().id(UUID.randomUUID()).build()));
        when(productRepository.count()).thenReturn(15L);
        when(userRepository.findByEmail(anyString()))
            .thenAnswer(call -> Optional.ofNullable(users.get(call.getArgument(0))));
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            if (user.getId() == null) user.setId(UUID.randomUUID());
            users.put(user.getEmail(), user);
            return user;
        });
    }

    @Test
    void seedsAdminAndEmployeeEvenWhenProductsAlreadyExist() {
        assertEquals(true, controller.seedAll().getBody().get("alreadySeeded"));
        User admin = users.get(ADMIN_EMAIL);
        assertNotNull(admin, "The designated admin must be provisioned before the already-seeded return");
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertFalse(Boolean.TRUE.equals(admin.getIsSystemAdmin()));
        assertEquals(UserRole.EMPLOYEE, users.get(EMPLOYEE_EMAIL).getRole());
        verify(membershipAuthorizer).grantMainSiteMembershipIfAbsent(admin.getId());
        verify(membershipAuthorizer).grantMainSiteMembershipIfAbsent(users.get(EMPLOYEE_EMAIL).getId());
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void reseedingReusesAdminAndPreservesIdentity() {
        UUID id = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        User existing = User.builder().id(id).email(ADMIN_EMAIL).fullName("Existing Name")
            .supabaseUserId(subject).role(UserRole.EMPLOYEE).build();
        users.put(ADMIN_EMAIL, existing);
        controller.seedAll();
        controller.seedAll();
        assertSame(existing, users.get(ADMIN_EMAIL));
        assertEquals(UserRole.ADMIN, existing.getRole());
        assertEquals(subject, existing.getSupabaseUserId());
        assertEquals("Existing Name", existing.getFullName());
        assertEquals(2, users.size());
        verify(membershipAuthorizer, times(2)).grantMainSiteMembershipIfAbsent(id);
    }
}
