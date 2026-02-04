import { apiGet, apiPut, apiDelete } from "./client";
import { Notification, NotificationCounts, NotificationSearchParams, PaginatedResponse } from "@/types/api";

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

/**
 * Search notifications with server-side pagination and filters
 */
export async function searchNotifications(
  params: NotificationSearchParams
): Promise<PaginatedResponse<Notification>> {
  const searchParams = new URLSearchParams();
  if (params.search) searchParams.set("search", params.search);
  if (params.type) searchParams.set("type", params.type);
  if (params.resolved !== undefined) searchParams.set("resolved", String(params.resolved));
  if (params.fromDate) searchParams.set("fromDate", params.fromDate);
  if (params.toDate) searchParams.set("toDate", params.toDate);
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const query = searchParams.toString();
  return apiGet<PaginatedResponse<Notification>>(`${BASE_PATH}/search${query ? `?${query}` : ""}`);
}

/**
 * Get notification counts for tabs
 */
export async function getNotificationCounts(): Promise<NotificationCounts> {
  return apiGet<NotificationCounts>(`${BASE_PATH}/counts`);
}

/**
 * Resolve a notification
 */
export async function resolveNotification(id: string): Promise<Notification> {
  return apiPut<Notification>(`${BASE_PATH}/${id}/resolve`);
}

/**
 * Unresolve a notification
 */
export async function unresolveNotification(id: string): Promise<Notification> {
  return apiPut<Notification>(`${BASE_PATH}/${id}/unresolve`);
}
