import { apiGet, apiPost, apiDelete } from "./client";
import { Invitation } from "@/types/api";

const BASE_PATH = "/api/admin/invitations";

export async function getPendingInvitations(): Promise<Invitation[]> {
  return apiGet<Invitation[]>(BASE_PATH);
}

export async function resendInvitation(email: string): Promise<Invitation> {
  return apiPost<Invitation>(`${BASE_PATH}/${encodeURIComponent(email)}/resend`);
}

export async function cancelInvitation(email: string): Promise<void> {
  return apiDelete<void>(`${BASE_PATH}/${encodeURIComponent(email)}`);
}
