import type { GovernanceProposal } from "../services/governanceService";

/** Short wallet label (first and last four characters). */
export function shortKey(key: string | null | undefined): string {
  if (!key) return "—";
  return key.length > 12 ? `${key.slice(0, 4)}…${key.slice(-4)}` : key;
}

/** One plain-language line for a proposal card. */
export function describeProposal(p: GovernanceProposal): string {
  if (p.kind === "withdraw") return `Withdraw ${p.amountUsdc ?? 0} USDC`;
  if (p.kind === "authorize_agent") {
    return p.permissions ? `Grant ${p.agentCode} powers` : `Revoke ${p.agentCode} powers`;
  }
  const n = p.newSignersCsv ? p.newSignersCsv.split(",").length : 0;
  return `New signer set: ${p.newThreshold} of ${n}`;
}

/** Splits a textarea into distinct trimmed wallets. */
export function parseWallets(text: string): string[] {
  return Array.from(
    new Set(
      text
        .split(/[\s,]+/)
        .map((w) => w.trim())
        .filter(Boolean),
    ),
  );
}

export const WALLET_RE = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;

/** On-chain agent permission bits (program v0.6.0+). */
export const PERMISSION_BITS = [
  { mask: 0x01, label: "Pedagogical (NEXUS): move Enigma levels" },
  { mask: 0x02, label: "Harmony: band operations" },
  { mask: 0x04, label: "Stage: approve venues and confirm bookings" },
  { mask: 0x08, label: "Vault: settle booking escrows" },
  { mask: 0x10, label: "Wave: royalty and catalog operations" },
];

export const PROPOSAL_KINDS = [
  { kind: "withdraw", label: "Withdraw treasury USDC", hint: "Pays one signer's USDC account." },
  { kind: "authorize_agent", label: "Agent powers", hint: "Grant or revoke on-chain agent bits." },
  { kind: "update_signers", label: "Change signers", hint: "New signer set and threshold." },
] as const;
