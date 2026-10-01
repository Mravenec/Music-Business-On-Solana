import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import bs58 from "bs58";
import {
  fetchCurrentSession,
  postWalletLocation,
  requestWalletChallenge,
  upsertWalletSession,
  type WalletSession,
} from "../services/sessionService";
import { getAccessToken, setAccessToken } from "../services/http";

type SessionState = {
  session: WalletSession | null;
  loading: boolean;
  error: string | null;
  geoError: string | null;
  refresh: () => Promise<void>;
};

const SessionContext = createContext<SessionState>({
  session: null,
  loading: false,
  error: null,
  geoError: null,
  refresh: async () => undefined,
});

function trackGeo(walletPubkey: string): Promise<WalletSession | null> {
  if (!navigator.geolocation) {
    return Promise.reject(new Error("Geolocation unavailable in this browser"));
  }
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const next = await postWalletLocation(
            walletPubkey,
            pos.coords.latitude,
            pos.coords.longitude
          );
          resolve(next);
        } catch (e) {
          reject(e);
        }
      },
      (err) => reject(new Error(err.message || "Location permission denied")),
      { enableHighAccuracy: false, timeout: 12000, maximumAge: 60_000 }
    );
  });
}

/**
 * Keeps MariaDB account in sync with the connected Solana wallet and tracks geo.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const { publicKey, connected, signMessage } = useWallet();
  const [session, setSession] = useState<WalletSession | null>(null);
  const [loading, setLoading] = useState(() => Boolean(getAccessToken()));
  const [error, setError] = useState<string | null>(null);
  const [geoError, setGeoError] = useState<string | null>(null);
  const wasConnected = useRef(false);

  const refresh = async () => {
    if (!publicKey || !signMessage) {
      return;
    }
    setLoading(true);
    setError(null);
    setGeoError(null);
    try {
      const pubkey = publicKey.toBase58();
      const challenge = await requestWalletChallenge(pubkey);
      const signed = await signMessage(new TextEncoder().encode(challenge.message));
      let next = await upsertWalletSession(pubkey, bs58.encode(signed));
      try {
        const withGeo = await trackGeo(pubkey);
        if (withGeo) next = withGeo;
      } catch (geoErr) {
        setGeoError(
          geoErr instanceof Error ? geoErr.message : "Could not record location"
        );
      }
      setSession(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "session failed");
      setSession(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchCurrentSession()
      .then((next) => {
        if (!cancelled) setSession(next);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const status =
          typeof e === "object" && e !== null && "response" in e
            ? (e as { response?: { status?: number } }).response?.status
            : undefined;
        if (status === 401) {
          setAccessToken(null);
          setSession(null);
          setError(null);
          return;
        }
        setError(e instanceof Error ? e.message : "session failed");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (connected) {
      wasConnected.current = true;
      if (!getAccessToken()) {
        void refresh();
      }
      return;
    }
    if (wasConnected.current) {
      wasConnected.current = false;
      setSession(null);
      setAccessToken(null);
      setError(null);
      setGeoError(null);
    }
  }, [connected, publicKey, signMessage]);

  return (
    <SessionContext.Provider
      value={{ session, loading, error, geoError, refresh }}
    >
      {children}
    </SessionContext.Provider>
  );
}

/**
 * Access the wallet-linked MariaDB session.
 */
export function useSession() {
  return useContext(SessionContext);
}
