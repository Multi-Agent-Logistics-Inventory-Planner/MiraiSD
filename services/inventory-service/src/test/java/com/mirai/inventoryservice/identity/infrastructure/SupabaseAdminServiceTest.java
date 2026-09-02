package com.mirai.inventoryservice.identity.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Supabase's admin list-users endpoint is paginated (default page size 50). A first version of
 * lookupSupabaseUserId only fetched page 1, so a matching account on a later page looked
 * identical to "account does not exist" - deleteUserByEmail then reported success without
 * actually deleting anything, leaving a ghost Supabase account. These tests pin the fix: the
 * lookup must keep paging until it finds the account or a short page proves there are no more.
 */
@ExtendWith(MockitoExtension.class)
class SupabaseAdminServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SupabaseAdminService service;

    private static String pageOfUsers(int count, String... matchingEmails) {
        StringBuilder json = new StringBuilder("{\"users\":[");
        for (int i = 0; i < count; i++) {
            String email = i < matchingEmails.length
                    ? matchingEmails[i]
                    : "filler" + i + "@example.com";
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"id-").append(i).append("\",\"email\":\"").append(email).append("\"}");
        }
        return json.append("]}").toString();
    }

    @BeforeEach
    void setUp() {
        service = new SupabaseAdminService(restTemplate);
        ReflectionTestUtils.setField(service, "supabaseUrl", "https://example.supabase.co");
        ReflectionTestUtils.setField(service, "serviceRoleKey", "test-key");
    }

    @Test
    void getSupabaseUserId_MatchOnFirstPage_ReturnsIdWithoutFetchingMore() {
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(200, "target@example.com")));

        String id = service.getSupabaseUserId("target@example.com");

        assertTrue(id != null && !id.isBlank());
        verify(restTemplate, times(1))
                .exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getSupabaseUserId_MatchOnSecondPage_KeepsPagingAndFindsIt() {
        // Full first page (no match) forces a second request; the account is on page 2.
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(200)));
        when(restTemplate.exchange(contains("?page=2&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(5, "target@example.com")));

        String id = service.getSupabaseUserId("target@example.com");

        assertTrue(id != null && !id.isBlank());
    }

    @Test
    void deleteUserByEmail_AccountOnSecondPage_ActuallyDeletesInsteadOfReportingGhostSuccess() {
        // The scenario that reproduced the ghost-account bug: without pagination this looked
        // identical to "no account", and delete reported success while nothing was deleted.
        when(restTemplate.exchange(contains("/admin/users?page=1"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(200)));
        when(restTemplate.exchange(contains("/admin/users?page=2"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(1, "target@example.com")));
        when(restTemplate.exchange(contains("/admin/users/id-0"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        boolean deleted = service.deleteUserByEmail("target@example.com");

        assertTrue(deleted);
        verify(restTemplate)
                .exchange(contains("/admin/users/id-0"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getSupabaseUserId_ShortPageWithNoMatch_StopsAndReturnsNull() {
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(3)));

        String id = service.getSupabaseUserId("nobody@example.com");

        assertTrue(id == null);
        verify(restTemplate, times(1))
                .exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void deleteUserByEmail_LookupFails_ReturnsFalseRatherThanAssumingAbsence() {
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "boom", null, null, null));

        boolean deleted = service.deleteUserByEmail("target@example.com");

        assertFalse(deleted, "a failed lookup must not be treated as confirmed absence");
    }

    @Test
    void getSupabaseUserId_ExceedsMaxPages_TreatsAsFailureNotAbsence() {
        // Every page is full with no match: the loop must give up loudly rather than return
        // null, since null would be indistinguishable from a genuinely confirmed absence.
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(200)));

        // getSupabaseUserId swallows the resulting exception to null (existence-check callers),
        // but deleteUserByEmail must not, since it gates a destructive operation.
        String id = service.getSupabaseUserId("target@example.com");
        assertTrue(id == null);

        assertFalse(service.deleteUserByEmail("target@example.com"));
    }

    @Test
    void deleteUserByEmail_NoAccountFound_IsIdempotentSuccess() {
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(0)));

        assertTrue(service.deleteUserByEmail("nobody@example.com"),
                "the desired end state already holds when no account exists");
    }

    @Test
    void deleteUserByEmail_MissingUsersField_TreatedAsFailureNotAbsence() {
        // A 200 with {} is not proof the account is absent - it's a response shape the code
        // doesn't understand. Conflating the two would let deleteUser delete the local user
        // while a real Supabase account survives.
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        assertFalse(service.deleteUserByEmail("target@example.com"));
    }

    @Test
    void deleteUserByEmail_NullUsersField_TreatedAsFailureNotAbsence() {
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"users\": null}"));

        assertFalse(service.deleteUserByEmail("target@example.com"));
    }

    @Test
    void getSupabaseUserId_EmptyUsersArray_IsGenuineAbsence() {
        // A real empty array, unlike a missing/null "users" field, is legitimate confirmation:
        // this must still resolve to null rather than being caught by the malformed-shape guard.
        when(restTemplate.exchange(contains("?page=1&"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pageOfUsers(0)));

        assertTrue(service.getSupabaseUserId("nobody@example.com") == null);
    }
}
