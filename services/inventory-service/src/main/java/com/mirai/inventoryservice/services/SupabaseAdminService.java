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
        String url = supabaseUrl + "/auth/v1/admin/generate_link";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "invite");
        body.put("email", email);
        body.put("redirect_to", invitationRedirectUrl);

        ObjectNode userData = objectMapper.createObjectNode();
        userData.put("role", role.toUpperCase());
        body.set("data", userData);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize invite request", e);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to generate invite link: {}", response.getBody());
                throw new RuntimeException("Failed to generate invite link: " + response.getStatusCode());
            }

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String actionLink = responseJson.get("action_link").asText();

            log.info("Generated invite link for {}", email);
            return actionLink;
        } catch (HttpClientErrorException e) {
            log.error("Supabase generate_link error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new RuntimeException("User already exists or email is invalid");
            }
            throw new RuntimeException("Failed to generate invite link: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing Supabase response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse invite response: " + e.getMessage());
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
