import { useMemo, type ComponentType, type ReactNode } from "react";
import type { Adapter } from "@solana/wallet-adapter-base";
import { ConnectionProvider, WalletProvider } from "@solana/wallet-adapter-react";
import { WalletModalProvider } from "@solana/wallet-adapter-react-ui";
import { PhantomWalletAdapter } from "@solana/wallet-adapter-phantom";
import { SolflareWalletAdapter } from "@solana/wallet-adapter-solflare";
import { useChainConfig } from "../hooks/useChainConfig";
import "@solana/wallet-adapter-react-ui/styles.css";

type AdapterTreeProps = { children?: ReactNode };

const ConnectionTree = ConnectionProvider as ComponentType<
  AdapterTreeProps & { endpoint: string; config?: { commitment: "confirmed" } }
>;
const WalletTree = WalletProvider as ComponentType<
  AdapterTreeProps & { wallets: Adapter[]; autoConnect?: boolean }
>;
const ModalTree = WalletModalProvider as ComponentType<AdapterTreeProps>;

/**
 * BagsCreatorFund-style wallet adapters. RPC comes from MariaDB chain_config, not a Mainnet deploy.
 */
export function SolanaProviders({ children }: { children: ReactNode }) {
  const { data } = useChainConfig();
  const endpoint = data?.rpcUrl ?? "https://api.devnet.solana.com";
  const wallets = useMemo(() => [new PhantomWalletAdapter(), new SolflareWalletAdapter()], []);

  return (
    <ConnectionTree endpoint={endpoint} config={{ commitment: "confirmed" }}>
      <WalletTree wallets={wallets} autoConnect={false}>
        <ModalTree>{children}</ModalTree>
      </WalletTree>
    </ConnectionTree>
  );
}
