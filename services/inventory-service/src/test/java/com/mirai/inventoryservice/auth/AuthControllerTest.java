package com.mirai.inventoryservice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @Test
    void testValidateToken_Success() throws Exception {
        // Arrange
        String validToken = "Bearer valid.jwt.token";
        String tokenWithoutBearer = "valid.jwt.token";
        
        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(true);
        when(jwtService.extractRole(tokenWithoutBearer)).thenReturn("ADMIN");
        when(jwtService.extractPersonId(tokenWithoutBearer)).thenReturn("user-123");
        when(jwtService.extractName(tokenWithoutBearer)).thenReturn("Admin User");

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.personId").value("user-123"))
                .andExpect(jsonPath("$.personName").value("Admin User"));
    }

    @Test
    void testValidateToken_InvalidToken() throws Exception {
        // Arrange
        String invalidToken = "Bearer invalid.jwt.token";
        String tokenWithoutBearer = "invalid.jwt.token";
        
        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", invalidToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Invalid token"));
    }

    @Test
    void testValidateToken_MissingAuthorizationHeader() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidateToken_ExpiredToken() throws Exception {
        // Arrange
        String expiredToken = "Bearer expired.jwt.token";
        String tokenWithoutBearer = "expired.jwt.token";
        
        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", expiredToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void testValidateToken_DifferentRoles() throws Exception {
        // Arrange - Test with EMPLOYEE role
        String token = "Bearer employee.jwt.token";
        String tokenWithoutBearer = "employee.jwt.token";
        
        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(true);
        when(jwtService.extractRole(tokenWithoutBearer)).thenReturn("EMPLOYEE");
        when(jwtService.extractPersonId(tokenWithoutBearer)).thenReturn("employee-456");
        when(jwtService.extractName(tokenWithoutBearer)).thenReturn("Employee User");

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
