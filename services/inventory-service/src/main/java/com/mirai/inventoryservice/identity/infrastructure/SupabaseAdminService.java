package com.mirai.inventoryservice.identity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class SupabaseAdminService {
    private static final Logger log = LoggerFactory.getLogger(SupabaseAdminService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceRoleKey;

    @Value("${invitation.redirect.url:http://localhost:3000/auth/accept-invite}")
    private String invitationRedirectUrl;

    /**
     * Takes RestTemplate via the app's shared bean (RestTemplateConfig) rather than
     * constructing its own, so tests can substitute a mock - the pagination behavior below is
     * easy to get wrong silently (see USER_LOOKUP_PAGE_SIZE) and needs direct coverage.
     */
    public SupabaseAdminService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates an invite link for a user without sending an email.
     * Uses Supabase Admin API to create the invite token.
     *
     * @param email the email address to invite
     * @param role the role to assign to the user
     * @return the invitation link
     */
    public String generateInviteLink(String email, String role) {
        return generateLink(email, role, "invite");
    }

    /**
     * Generates a magic link for an existing user.
     * Used when resending invites to users who already exist in Supabase.
     *
     * @param email the email address
     * @param role the role (for metadata)
     * @return the magic link
     */
    public String generateMagicLink(String email, String role) {
        return generateLink(email, role, "magiclink");
    }

    /**
     * Checks if a user already exists in Supabase auth.
     *
     * @param email the email to check
     * @return true if user exists
     */
    public boolean userExistsInSupabase(String email) {
        return getSupabaseUserId(email) != null;
    }

    /**
     * Gets the Supabase user ID for an email address.
     *
     * @param email the email to look up
     * @return the Supabase user ID, or null if not found
     */
    public String getSupabaseUserId(String email) {
        try {
            return lookupSupabaseUserId(email);
        } catch (Exception e) {
            log.warn("Failed to get Supabase user ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Supabase's admin list-users endpoint is paginated (default page size 50). Fetching only
     * page 1 previously meant a match on a later page looked identical to "account does not
     * exist" - deleteUserByEmail would then report success without actually deleting anything,
     * leaving a ghost account past user #50. A generous page size keeps this to one request for
     * any installation this app's scale will realistically reach, while MAX_PAGES bounds the
     * loop against a runaway response instead of assuming that holds forever.
     */
    private static final int USER_LOOKUP_PAGE_SIZE = 200;
    private static final int USER_LOOKUP_MAX_PAGES = 50;

    /**
     * Looks up a Supabase user ID, returning null only when the account genuinely does not
     * exist and propagating anything else. {@link #getSupabaseUserId} collapses both outcomes
     * to null, which is fine for existence checks but unsafe for callers that must not treat a
     * failed lookup as "confirmed absent".
     */
    private String lookupSupabaseUserId(String email) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        for (int page = 1; page <= USER_LOOKUP_MAX_PAGES; page++) {
            String url = supabaseUrl + "/auth/v1/admin/users?page=" + page
                    + "&per_page=" + USER_LOOKUP_PAGE_SIZE;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode users = responseJson.get("users");
            if (users == null || !users.isArray()) {
                // A 200 with a missing/non-array "users" field (e.g. {} or {"users": null}) is
                // an unexpected shape, not evidence the account is absent - conflating the two
                // would let deleteUserByEmail treat a malformed response as confirmed absence
                // and proceed to delete the local user while a real Supabase account survives.
                throw new IllegalStateException(
                        "Unexpected Supabase list-users response shape (page " + page + "): "
                                + response.getBody());
            }
            if (users.isEmpty()) {
                // A genuinely empty array - on page 1 there are no users at all; on a later
                // page it means the previous page already covered everyone. Either way this is
                // real confirmation of absence, not a malformed response.
                return null;
            }

            for (JsonNode user : users) {
                if (email.equalsIgnoreCase(user.get("email").asText())) {
                    return user.get("id").asText();
                }
            }

            if (users.size() < USER_LOOKUP_PAGE_SIZE) {
                // Short page: this was the last one.
                return null;
            }
        }

        // Giving up here is not the same as confirming absence - returning null would let a
        // caller (e.g. deleteUserByEmail) treat an inconclusive search as "account does not
        // exist", the exact bug this pagination fix addresses. Fail instead.
        throw new IllegalStateException("Exceeded " + USER_LOOKUP_MAX_PAGES
                + " pages while searching Supabase users for " + email);
    }

    /**
     * Deletes a user from Supabase auth by their email.
     *
     * @param email the email of the user to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteUserByEmail(String email) {
        String userId;
        try {
            userId = lookupSupabaseUserId(email);
        } catch (Exception e) {
            // A failed lookup is not proof the account is gone - report failure rather than
            // letting a caller conclude the delete succeeded.
            log.error("Failed to look up Supabase user before delete: {}", e.getMessage());
            return false;
        }

        if (userId == null) {
            // Idempotent: the account is confirmed absent, so the desired end state already
            // holds and callers gating on the result must not treat this as a failure.
            log.info("No Supabase user found for email: {}", email);
            return true;
        }

        String url = supabaseUrl + "/auth/v1/admin/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            log.info("Deleted Supabase user: {}", email);
            return true;
        } catch (HttpClientErrorException e) {
            log.error("Failed to delete Supabase user: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error deleting Supabase user: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Updates a user's metadata in Supabase auth.
     *
     * @param email the email of the user to update
     * @param name the new display name (null to skip)
     * @param role the new role (null to skip)
     * @return true if updated successfully, false otherwise
     */
    public boolean updateUserMetadata(String email, String name, String role) {
        String userId = getSupabaseUserId(email);
        if (userId == null) {
            log.info("No Supabase user found for email: {}", email);
            return false;
        }

        String url = supabaseUrl + "/auth/v1/admin/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode userMetadata = objectMapper.createObjectNode();
        if (name != null) userMetadata.put("name", name);
        if (role != null) userMetadata.put("role", role.toUpperCase());
        body.set("user_metadata", userMetadata);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            log.error("Failed to serialize update request: {}", e.getMessage());
            return false;
        }

        try {
            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            log.info("Updated Supabase user metadata for: {}", email);
            return true;
        } catch (HttpClientErrorException e) {
            log.error("Failed to update Supabase user: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error updating Supabase user: {}", e.getMessage());
            return false;
        }
    }

    private String generateLink(String email, String role, String type) {
        String url = supabaseUrl + "/auth/v1/admin/generate_link";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", type);
        body.put("email", email);
        body.put("redirect_to", invitationRedirectUrl);

        ObjectNode userData = objectMapper.createObjectNode();
        userData.put("role", role.toUpperCase());
        body.set("data", userData);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to generate {} link: {}", type, response.getBody());
                throw new RuntimeException("Failed to generate link: " + response.getStatusCode());
            }

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String actionLink = responseJson.get("action_link").asText();

            log.info("Generated {} link for {}", type, email);
            return actionLink;
        } catch (HttpClientErrorException e) {
            log.error("Supabase generate_link error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new RuntimeException("User already exists or email is invalid");
            }
            throw new RuntimeException("Failed to generate link: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing Supabase response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse response: " + e.getMessage());
        }
    }

    /**
     * @deprecated Use {@link #generateInviteLink(String, String)} with EmailService instead
     */
    @Deprecated
    public void inviteUserByEmail(String email, String role) {
        generateInviteLink(email, role);
    }

    /**
     * @deprecated Use {@link #generateInviteLink(String, String)} with EmailService instead
     */
    @Deprecated
    public void resendInvitation(String email, String role) {
        generateInviteLink(email, role);
    }
}
