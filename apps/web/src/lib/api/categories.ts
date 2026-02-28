import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { Category, CategoryRequest } from "@/types/api";

const BASE_PATH = "/api/categories";

/**
 * Get all active root categories with their children (hierarchy)
 */
export async function getCategories(): Promise<Category[]> {
  return apiGet<Category[]>(BASE_PATH);
}

/**
 * Get a category by ID
 */
export async function getCategoryById(id: string): Promise<Category> {
  return apiGet<Category>(`${BASE_PATH}/${id}`);
}

/**
 * Get children of a category
 */
export async function getChildCategories(parentId: string): Promise<Category[]> {
  return apiGet<Category[]>(`${BASE_PATH}/${parentId}/children`);
}

/**
 * Create a new category (can be root or child based on parentId)
 */
export async function createCategory(data: CategoryRequest): Promise<Category> {
  return apiPost<Category, CategoryRequest>(BASE_PATH, data);
}

/**
 * Update a category
 */
export async function updateCategory(
  id: string,
  data: CategoryRequest
): Promise<Category> {
  return apiPut<Category, CategoryRequest>(`${BASE_PATH}/${id}`, data);
}

/**
 * Delete a category
 */
export async function deleteCategory(id: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${id}`);
}
