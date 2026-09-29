import { apiClient as client } from "./http";
import type { BuiltIx } from "./sendBuiltIx";
import type { Booking, ConcertSettlement, Venue } from "./venueService";

/** JOOQ Venue POJO keys for the on-chain listing (program v0.7.0). */
export type ListedVenue = Venue & {
  contractTypeId?: number | null;
  walletPubkey: string | null;
  listingStatus: "pending" | "approved" | null;
  registerTxSignature: string | null;
  approveTxSignature: string | null;
  approvedByWallet: string | null;
};

/** JOOQ Booking POJO keys for the VenueAccessToken escrow. */
export type EscrowBooking = Booking & {
  venueAccessTokenPda: string | null;
  contractHash: string | null;
  onchainConcertId: number | null;
  grossUsdc: number | null;
  escrowPda: string | null;
  contractText: string | null;
  onchainStatus: "proposed" | "confirmed" | "settled" | "cancelled" | null;
  proposeTxSignature: string | null;
  confirmTxSignature: string | null;
  cancelTxSignature: string | null;
};

/** JOOQ ConcertSettlement POJO keys after settle_booking. */
export type EscrowSettlement = ConcertSettlement & {
  settleTxSignature: string | null;
  onChainStatus: string | null;
  settledVia: string | null;
  settledByWallet: string | null;
};

export type VenueConsole = {
  programId: string;
  venue: ListedVenue;
  bookings: EscrowBooking[];
};

export type StageQueue = {
  signerWalletPubkey: string;
  signerRole: "owner" | "stage_agent";
  pendingVenues: ListedVenue[];
  proposedBookings: EscrowBooking[];
};

export type VaultQueue = {
  signerWalletPubkey: string;
  signerRole: "owner" | "vault_agent";
  confirmedBookings: EscrowBooking[];
};

export type StepBuild = BuiltIx & { instruction: string; note: string };

export type ConfirmContractBuild = StepBuild & { contractText: string; contractHashHex: string };

export type SettleBookingBuild = StepBuild & {
  grossUsdc: number;
  expensesUsdc: number;
  netUsdc: number;
  eh8sFeeUsdc: number;
  bandPoolUsdc: number;
  protocolFeeBps: number;
  memberWallets: string[];
  memberPendingUsdc: number[];
};

type WalletBody = { walletPubkey: string };
type SignedBody = WalletBody & { txSignature: string };

function requireSignature(txSignature: string) {
  if (!txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to record without a DevNet signature");
  }
}

async function post<T>(url: string, body: object): Promise<T> {
  const { data } = await client.post<T>(url, body);
  return data;
}

/** Venue listing state plus its bookings. */
export async function fetchVenueConsole(venueId: number): Promise<VenueConsole> {
  const { data } = await client.get<VenueConsole>(`/api/venues/${venueId}/onchain`);
  return data;
}

/** Pending listings + proposed bookings (owner or STAGE agent; 403 otherwise). */
export async function fetchStageQueue(walletPubkey: string): Promise<StageQueue> {
  const { data } = await client.get<StageQueue>("/api/stage/queue", { params: { walletPubkey } });
  return data;
}

/** Confirmed escrows waiting for settle_booking (owner or VAULT agent; 403 otherwise). */
export async function fetchVaultQueue(walletPubkey: string): Promise<VaultQueue> {
  const { data } = await client.get<VaultQueue>("/api/vault/settle-queue", {
    params: { walletPubkey },
  });
  return data;
}

/** Builds register_venue for the connected venue wallet. */
export function buildRegisterVenue(venueId: number, body: WalletBody): Promise<StepBuild> {
  return post(`/api/venues/${venueId}/register/build`, body);
}

/** Records a DevNet-confirmed register_venue (listing pending). */
export function confirmRegisterVenue(venueId: number, body: SignedBody): Promise<ListedVenue> {
  requireSignature(body.txSignature);
  return post(`/api/venues/${venueId}/register/confirm`, body);
}

/** Builds approve_venue for the owner or a STAGE agent. */
export function buildApproveVenue(venueId: number, body: WalletBody): Promise<StepBuild> {
  return post(`/api/venues/${venueId}/approve/build`, body);
}

/** Records a DevNet-confirmed approve_venue. */
export function confirmApproveVenue(venueId: number, body: SignedBody): Promise<ListedVenue> {
  requireSignature(body.txSignature);
  return post(`/api/venues/${venueId}/approve/confirm`, body);
}

/** Builds propose_booking: the venue escrows the gross. */
export function buildProposeBooking(
  bookingId: number,
  body: WalletBody & { grossUsdc: string },
): Promise<StepBuild> {
  return post(`/api/bookings/${bookingId}/propose/build`, body);
}

/** Records a DevNet-confirmed propose_booking. */
export function confirmProposeBooking(
  bookingId: number,
  body: SignedBody & { grossUsdc: string },
): Promise<EscrowBooking> {
  requireSignature(body.txSignature);
  return post(`/api/bookings/${bookingId}/propose/confirm`, body);
}

/** Builds confirm_booking; the response carries the contract text whose SHA-256 is pinned. */
export function buildConfirmContract(
  bookingId: number,
  body: WalletBody,
): Promise<ConfirmContractBuild> {
  return post(`/api/bookings/${bookingId}/confirm-contract/build`, body);
}

/** Records a DevNet-confirmed confirm_booking. */
export function confirmConfirmContract(bookingId: number, body: SignedBody): Promise<EscrowBooking> {
  requireSignature(body.txSignature);
  return post(`/api/bookings/${bookingId}/confirm-contract/confirm`, body);
}

/** Builds cancel_booking (venue, while proposed). */
export function buildCancelBooking(bookingId: number, body: WalletBody): Promise<StepBuild> {
  return post(`/api/bookings/${bookingId}/cancel/build`, body);
}

/** Records a DevNet-confirmed cancel_booking. */
export function confirmCancelBooking(bookingId: number, body: SignedBody): Promise<EscrowBooking> {
  requireSignature(body.txSignature);
  return post(`/api/bookings/${bookingId}/cancel/confirm`, body);
}

/** Builds settle_booking with the fee / pool / member preview. */
export function buildSettleBooking(bookingId: number, body: WalletBody): Promise<SettleBookingBuild> {
  return post(`/api/bookings/${bookingId}/settle/build`, body);
}

/** Records a DevNet-confirmed settle_booking (settlement + member claims). */
export function confirmSettleBooking(bookingId: number, body: SignedBody): Promise<EscrowSettlement> {
  requireSignature(body.txSignature);
  return post(`/api/bookings/${bookingId}/settle/confirm`, body);
}
