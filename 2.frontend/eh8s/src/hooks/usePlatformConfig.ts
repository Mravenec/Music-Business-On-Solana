import { useEffect, useState } from "react";
import { fetchPlatformConfig, type PlatformConfig } from "../services/sessionService";
import { useSession } from "./useSession";

/**
 * Public platform settings (owner wallet, protocol fee); reloads when the signed-in account changes.
 */
export function usePlatformConfig(): PlatformConfig | null {
  const { session } = useSession();
  const [platform, setPlatform] = useState<PlatformConfig | null>(null);

  useEffect(() => {
    void fetchPlatformConfig()
      .then(setPlatform)
      .catch(() => setPlatform(null));
  }, [session?.accessToken, session?.account?.id]);

  return platform;
}
