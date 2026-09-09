import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import { webApiClient, unwrapGeneratedResponse } from "./generated-client";
import type { components } from "@mirai/api-client";
import { Category, CategoryRequest } from "@/types/api";

const BASE_PATH = "/api/categories";

/**
 * Get all active root categories with their children (hierarchy)
 *
 * @deprecated Superseded by getCatalogCategories (phase-5d T-6) - kept only for callers not
 * yet migrated. Categories are wholly global (no site scoping), so this and the v1 read return
 * identical data; there is no functional reason left to prefer this one.
 */
export async function getCategories(): Promise<Category[]> {
  return apiGet<Category[]>(BASE_PATH);
}

// `id`/`name` are a category's identity - a record missing either is dropped (and, since
// children are recursive, so is its subtree) rather than defaulted, which would otherwise
// silently match nothing or collide with another dropped record wherever a caller keys or
// filters by id.
function toCategory(
  dto: components["schemas"]["CategoryResponseDTO"]
): Category | null {
  if (!dto.id || !dto.name) {
    return null;
  }
  return {
    id: dto.id,
    parentId: dto.parentId ?? null,
    name: dto.name,
    slug: dto.slug ?? "",
    displayOrder: dto.displayOrder ?? 0,
    isActive: dto.isActive ?? false,
    usesPacks: dto.usesPacks ?? false,
    children: (dto.children ?? [])
      .map(toCategory)
      .filter((c): c is Category => c !== null),
    createdAt: dto.createdAt ?? "",
    updatedAt: dto.updatedAt ?? "",
  };
}

/**
 * Get all root categories with their children (hierarchy) from the global catalog v1 route
 * (spec.md phase-5d AC-7). Categories are wholly global - no siteId, unlike products.
 */
export async function getCatalogCategories(): Promise<Category[]> {
  const result = await webApiClient.GET("/api/v1/catalog/categories", {});
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toCategory)
    .filter((c): c is Category => c !== null);
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
