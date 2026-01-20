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

    public void inviteUserByEmail(String email, String role) {
        String url = supabaseUrl + "/auth/v1/invite";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);

        ObjectNode userData = objectMapper.createObjectNode();
        userData.put("role", role.toUpperCase());
        body.set("data", userData);

        body.put("redirect_to", invitationRedirectUrl);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize invite request", e);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to invite user: {}", response.getBody());
                throw new RuntimeException("Failed to invite user: " + response.getStatusCode());
            }

            log.info("Successfully sent invitation to {}", email);
        } catch (HttpClientErrorException e) {
            log.error("Supabase invite error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new RuntimeException("User already exists or email is invalid");
            }
            throw new RuntimeException("Failed to invite user: " + e.getMessage());
        }
    }

    public void resendInvitation(String email, String role) {
        inviteUserByEmail(email, role);
    }
}
