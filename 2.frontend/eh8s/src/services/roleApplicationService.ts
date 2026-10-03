import { apiClient } from "./http";

export type RoleApplication = {
  id: number;
  accountId: number;
  walletPubkey: string;
  role: string;
  status: string;
  reason?: string | null;
  reviewedByWallet?: string | null;
  createdAt?: string | null;
  reviewedAt?: string | null;
};

export type AccountRole = {
  id: number;
  accountId: number;
  role: string;
  grantedAt?: string | null;
  applicationId?: number | null;
};

export const APPLYABLE_ROLES = ["student", "musician", "instructor", "venue"] as const;

/**
 * Submits a role application for the connected non-owner wallet.
 */
export async function applyForRole(
  walletPubkey: string,
  role: string
): Promise<RoleApplication> {
  const { data } = await apiClient.post<RoleApplication>("/api/role-applications", {
    walletPubkey,
    role,
  });
  return data;
}

/**
 * Lists role applications, optionally filtered.
 */
export async function fetchRoleApplications(
  status?: string,
  walletPubkey?: string
): Promise<RoleApplication[]> {
  const { data } = await apiClient.get<RoleApplication[]>("/api/role-applications", {
    params: {
      ...(status ? { status } : {}),
      ...(walletPubkey ? { walletPubkey } : {}),
    },
  });
  return data;
}

/**
 * Owner approves a pending application.
 */
export async function approveRoleApplication(
  id: number,
  reviewedByWallet: string,
  reason?: string
): Promise<RoleApplication> {
  const { data } = await apiClient.post<RoleApplication>(
    `/api/role-applications/${id}/approve`,
    { reviewedByWallet, reason }
  );
  return data;
}

/**
 * Owner rejects a pending application.
 */
export async function rejectRoleApplication(
  id: number,
  reviewedByWallet: string,
  reason?: string
): Promise<RoleApplication> {
  const { data } = await apiClient.post<RoleApplication>(
    `/api/role-applications/${id}/reject`,
    { reviewedByWallet, reason }
  );
  return data;
}

/**
 * Owner revokes an approved role. The grant is removed so the wallet can apply again.
 */
export async function revokeRoleApplication(
  id: number,
  reviewedByWallet: string,
  reason?: string
): Promise<RoleApplication> {
  const { data } = await apiClient.post<RoleApplication>(
    `/api/role-applications/${id}/revoke`,
    { reviewedByWallet, reason }
  );
  return data;
}

/**
 * Lists approved multi-role memberships for a wallet.
 */
/**
 * Principal grants studio admin. A studio admin may grant partner.
 */
export async function grantRole(walletPubkey: string, role: string): Promise<RoleApplication> {
  const { data } = await apiClient.post<RoleApplication>("/api/role-grants", {
    walletPubkey,
    role,
  });
  return data;
}

export async function fetchRoleMemberships(
  walletPubkey: string
): Promise<AccountRole[]> {
  const { data } = await apiClient.get<AccountRole[]>("/api/roles/memberships", {
    params: { walletPubkey },
  });
  return data;
}
