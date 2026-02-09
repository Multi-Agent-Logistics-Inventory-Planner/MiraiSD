package com.mirai.inventoryservice.controllers.security;

import com.mirai.inventoryservice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for NotificationController.
 * Authorization matrix:
 * - GET: EMPLOYEE+ (notifications are system-generated, employees need to see them)
 * - PUT (mark read/resolve): EMPLOYEE+ (users can mark their notifications)
 * - DELETE: ADMIN only
 */
@DisplayName("NotificationController Security Tests")
class NotificationControllerSecurityIT extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/notifications";

    @Nested
    @DisplayName("GET /api/notifications")
    class GetAllNotificationsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getAllNotifications_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getAllNotifications_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getAllNotifications_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow ADMIN role to access")
        void getAllNotifications_adminRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/unread")
    class GetUnreadNotificationsTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getUnreadNotifications_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/unread")
                            .param("recipientId", "550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getUnreadNotifications_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/unread")
                            .param("recipientId", "550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getUnreadNotifications_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/unread")
                            .param("recipientId", "550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/{id}")
    class GetNotificationByIdTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void getNotificationById_noAuth_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to access")
        void getNotificationById_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to access")
        void getNotificationById_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/{id}/read")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void markAsRead_noAuth_returns401() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/read"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to mark as read")
        void markAsRead_userRole_returns403() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/read")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to mark as read")
        void markAsRead_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/read")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/read-all")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void markAllAsRead_noAuth_returns401() throws Exception {
            mockMvc.perform(put(BASE_URL + "/read-all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts")
        void markAllAsRead_userRole_returns403() throws Exception {
            mockMvc.perform(put(BASE_URL + "/read-all")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role")
        void markAllAsRead_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(put(BASE_URL + "/read-all")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/{id}/resolve")
    class ResolveNotificationTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void resolveNotification_noAuth_returns401() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/resolve"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts")
        void resolveNotification_userRole_returns403() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/resolve")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role")
        void resolveNotification_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(put(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000/resolve")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications/{id}")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should return 401 when no token provided")
        void deleteNotification_noAuth_returns401() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 when USER role attempts to delete")
        void deleteNotification_userRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 when EMPLOYEE role attempts to delete")
        void deleteNotification_employeeRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow ADMIN role to delete")
        void deleteNotification_adminRole_notForbidden() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/550e8400-e29b-41d4-a716-446655440000")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/search")
    class SearchNotificationsTests {

        @Test
        @DisplayName("Should return 403 when USER role attempts to search")
        void searchNotifications_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to search")
        void searchNotifications_employeeRole_notForbidden() throws Exception {
            mockMvc.perform(get(BASE_URL + "/search")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/counts")
    class GetNotificationCountsTests {

        @Test
        @DisplayName("Should return 403 when USER role attempts to get counts")
        void getNotificationCounts_userRole_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL + "/counts")
                            .header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should allow EMPLOYEE role to get counts")
        void getNotificationCounts_employeeRole_returns200() throws Exception {
            mockMvc.perform(get(BASE_URL + "/counts")
                            .header("Authorization", "Bearer " + employeeToken()))
                    .andExpect(status().isOk());
        }
    }
}
