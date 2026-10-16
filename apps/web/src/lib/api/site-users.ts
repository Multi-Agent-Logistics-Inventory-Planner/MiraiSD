import { apiGet } from "./client";
import { User } from "@/types/api";

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/users`;
}

export async function getSiteUsers(siteId: string): Promise<User[]> {
  return apiGet<User[]>(basePath(siteId));
}

export async function getSiteLastAudits(siteId: string): Promise<Record<string, string>> {
  return apiGet<Record<string, string>>(`${basePath(siteId)}/last-audits`);
}
