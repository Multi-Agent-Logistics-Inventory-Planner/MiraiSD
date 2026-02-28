package com.mirai.inventoryservice.services;

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

    public SupabaseAdminService() {
        this.restTemplate = new RestTemplate();
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
        String url = supabaseUrl + "/auth/v1/admin/users";

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode users = responseJson.get("users");

            if (users != null && users.isArray()) {
                for (JsonNode user : users) {
                    if (email.equalsIgnoreCase(user.get("email").asText())) {
                        return user.get("id").asText();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get Supabase user ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a user from Supabase auth by their email.
     *
     * @param email the email of the user to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteUserByEmail(String email) {
        String userId = getSupabaseUserId(email);
        if (userId == null) {
            log.info("No Supabase user found for email: {}", email);
            return false;
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
     * @param name the new display name
     * @return true if updated successfully, false otherwise
     */
    public boolean updateUserMetadata(String email, String name) {
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
        userMetadata.put("name", name);
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
