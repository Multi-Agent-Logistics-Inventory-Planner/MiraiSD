import { apiGet, apiPut, apiDelete } from "./client";
import { Notification } from "@/types/api";

const BASE_PATH = "/api/notifications";

/**
 * Get all notifications
 */
export async function getNotifications(recipientId?: string): Promise<Notification[]> {
  const params = recipientId ? `?recipientId=${recipientId}` : "";
  return apiGet<Notification[]>(`${BASE_PATH}${params}`);
}

/**
 * Get unread notifications for a recipient
 */
export async function getUnreadNotifications(recipientId: string): Promise<Notification[]> {
  return apiGet<Notification[]>(`${BASE_PATH}/unread?recipientId=${recipientId}`);
}

/**
 * Get a notification by ID
 */
export async function getNotificationById(id: string): Promise<Notification> {
  return apiGet<Notification>(`${BASE_PATH}/${id}`);
}

/**
 * Mark a notification as read
 */
export async function markNotificationAsRead(id: string): Promise<Notification> {
  return apiPut<Notification>(`${BASE_PATH}/${id}/read`);
}

/**
 * Mark all notifications as read
 */
export async function markAllNotificationsAsRead(): Promise<void> {
  return apiPut<void>(`${BASE_PATH}/read-all`);
}

/**
 * Delete a notification
 */
export async function deleteNotification(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}

