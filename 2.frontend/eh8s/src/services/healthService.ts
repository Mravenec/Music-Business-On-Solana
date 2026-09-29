import { apiClient as client } from "./http";

export type HealthPayload = {
  status: string;
  module?: string;
  product?: string;
};

/**
 * Fetches live Health from Spring via the Vite proxy.
 */
export async function fetchHealth(): Promise<HealthPayload> {
  const { data } = await client.get<HealthPayload>("/health");
  return data;
}
