package com.mirai.inventoryservice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirai.inventoryservice.dtos.mappers.UserMapper;
import com.mirai.inventoryservice.models.audit.User;
import com.mirai.inventoryservice.models.enums.UserRole;
import com.mirai.inventoryservice.services.InvitationService;
import com.mirai.inventoryservice.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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

    @MockBean
    private UserService userService;

    @MockBean
    private InvitationService invitationService;

    @MockBean
    private UserMapper userMapper;

    @Test
    void testValidateToken_Success() throws Exception {
        // Arrange
        String validToken = "Bearer valid.jwt.token";
        String tokenWithoutBearer = "valid.jwt.token";
        String email = "admin@example.com";
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        User user = User.builder()
                .id(userId)
                .email(email)
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();

        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(true);
        when(jwtService.extractRole(tokenWithoutBearer)).thenReturn("ADMIN");
        when(jwtService.extractName(tokenWithoutBearer)).thenReturn("Admin User");
        when(jwtService.extractEmail(tokenWithoutBearer)).thenReturn(email);
        when(userService.existsByEmail(email)).thenReturn(true);
        when(userService.getUserByEmail(email)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.personId").value(userId.toString()))
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
        String email = "employee@example.com";
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        User user = User.builder()
                .id(userId)
                .email(email)
                .fullName("Employee User")
                .role(UserRole.EMPLOYEE)
                .build();

        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(true);
        when(jwtService.extractRole(tokenWithoutBearer)).thenReturn("EMPLOYEE");
        when(jwtService.extractName(tokenWithoutBearer)).thenReturn("Employee User");
        when(jwtService.extractEmail(tokenWithoutBearer)).thenReturn(email);
        when(userService.existsByEmail(email)).thenReturn(true);
        when(userService.getUserByEmail(email)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void testValidateToken_UserNotInDatabase() throws Exception {
        // Arrange - User exists in Supabase but not in application database
        String token = "Bearer new.user.token";
        String tokenWithoutBearer = "new.user.token";
        String email = "newuser@example.com";

        when(jwtService.validateToken(tokenWithoutBearer)).thenReturn(true);
        when(jwtService.extractRole(tokenWithoutBearer)).thenReturn("EMPLOYEE");
        when(jwtService.extractName(tokenWithoutBearer)).thenReturn("New User");
        when(jwtService.extractEmail(tokenWithoutBearer)).thenReturn(email);
        when(userService.existsByEmail(email)).thenReturn(false);

        // Act & Assert - personId should be null if user not in database
        mockMvc.perform(post("/api/auth/validate")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.personId").doesNotExist());
    }
}
