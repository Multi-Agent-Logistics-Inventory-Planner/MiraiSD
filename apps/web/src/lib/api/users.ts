import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { User, UserRequest } from "@/types/api";

const BASE_PATH = "/api/users";

/**
 * Get all users
 */
export async function getUsers(): Promise<User[]> {
  return apiGet<User[]>(BASE_PATH);
}

/**
 * Get a user by ID
 */
export async function getUserById(id: string): Promise<User> {
  return apiGet<User>(`${BASE_PATH}/${id}`);
}

/**
 * Get a user by email
 */
export async function getUserByEmail(email: string): Promise<User> {
  return apiGet<User>(`${BASE_PATH}/email/${encodeURIComponent(email)}`);
}

/**
 * Get a user by full name
 */
export async function getUserByName(fullName: string): Promise<User> {
  return apiGet<User>(`${BASE_PATH}/name/${encodeURIComponent(fullName)}`);
}

/**
 * Create a new user
 */
export async function createUser(data: UserRequest): Promise<User> {
  return apiPost<User, UserRequest>(BASE_PATH, data);
}

/**
 * Update an existing user
 */
export async function updateUser(id: string, data: UserRequest): Promise<User> {
  return apiPut<User, UserRequest>(`${BASE_PATH}/${id}`, data);
}

/**
 * Delete a user
 */
export async function deleteUser(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}
