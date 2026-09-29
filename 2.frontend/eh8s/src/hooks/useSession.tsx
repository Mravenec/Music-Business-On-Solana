import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import bs58 from "bs58";
import {
  postWalletLocation,
  requestWalletChallenge,
  upsertWalletSession,
  type WalletSession,
} from "../services/sessionService";
import { setAccessToken } from "../services/http";

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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [geoError, setGeoError] = useState<string | null>(null);

  const refresh = async () => {
    if (!publicKey) {
      setSession(null);
      setAccessToken(null);
      return;
    }
    setLoading(true);
    setError(null);
    setGeoError(null);
    try {
      const pubkey = publicKey.toBase58();
      if (!signMessage) {
        throw new Error("This wallet cannot sign the login message");
      }
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
    if (!connected || !publicKey) {
      setSession(null);
      setAccessToken(null);
      setError(null);
      setGeoError(null);
      return;
    }
    void refresh();
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
