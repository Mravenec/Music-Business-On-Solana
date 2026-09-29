import { apiClient as client } from "./http";

export type Instrument = { id: number; code: string; name: string };
export type EnigmaLevel = {
  id: number;
  levelNumber: number;
  name: string;
  durationNote: string;
  milestone: string;
};
export type AcademyPlan = {
  id: number;
  code: string;
  name: string;
  usdcMonthly: number;
  /** u8 priced by subscribe_academy (1 basic, 2 band, 3 pro). */
  onchainPlanType?: number | null;
  description: string;
};
export type MusicianProfile = {
  id: number;
  accountId: number;
  instrumentId: number;
  enigmaLevelId: number;
  enigmaScore: number;
  musicianProfilePda: string | null;
  countryCode?: string | null;
};
export type AcademySubscription = {
  id: number;
  musicianProfileId: number;
  academyPlanId: number;
  instructorProfileId?: number | null;
  startsAt?: string;
  expiresAt?: string;
  /** Months bought by this row (1..12). */
  months?: number;
  treasuryUsdc?: number;
  instructorUsdc?: number;
  academySubscriptionPda?: string | null;
  intendedInstruction?: string;
  payTxSignature?: string | null;
  paidAt?: string | null;
  payerWalletPubkey?: string | null;
  onChainStatus?: string | null;
  /** expires_at read from the AcademySubscription PDA after confirm. */
  onchainExpiresAt?: string | null;
};
export type EnigmaEvaluation = {
  id: number;
  musicianProfileId: number;
  weekStart?: string;
  score: number;
  notes?: string | null;
};

export async function fetchInstruments(): Promise<Instrument[]> {
  const { data } = await client.get<Instrument[]>("/api/instruments");
  return data;
}

export async function fetchLevels(): Promise<EnigmaLevel[]> {
  const { data } = await client.get<EnigmaLevel[]>("/api/enigma-levels");
  return data;
}

export async function fetchPlans(): Promise<AcademyPlan[]> {
  const { data } = await client.get<AcademyPlan[]>("/api/academy-plans");
  return data;
}

export async function fetchMusicians(): Promise<MusicianProfile[]> {
  const { data } = await client.get<MusicianProfile[]>("/api/musicians");
  return data;
}

export async function fetchSubscriptions(): Promise<AcademySubscription[]> {
  const { data } = await client.get<AcademySubscription[]>("/api/academy-subscriptions");
  return data;
}

export async function fetchEvaluations(): Promise<EnigmaEvaluation[]> {
  const { data } = await client.get<EnigmaEvaluation[]>("/api/enigma-evaluations");
  return data;
}

/** Creates the musician profile; the backend always starts it at Enigma level 0. */
export async function createMusician(body: {
  accountId: number;
  instrumentId: number;
  countryCode: string;
}): Promise<MusicianProfile> {
  const { data } = await client.post<MusicianProfile>("/api/musicians", body);
  return data;
}

export async function createSubscription(body: {
  musicianProfileId: number;
  academyPlanId: number;
  instructorProfileId?: number | null;
  months?: number;
}): Promise<AcademySubscription> {
  const { data } = await client.post<AcademySubscription>("/api/academy-subscriptions", body);
  return data;
}

export async function createEvaluation(body: {
  musicianProfileId: number;
  score: number;
  notes?: string;
}): Promise<EnigmaEvaluation> {
  const { data } = await client.post<EnigmaEvaluation>("/api/enigma-evaluations", body);
  return data;
}
