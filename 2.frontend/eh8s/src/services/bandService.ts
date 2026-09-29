import { apiClient as client } from "./http";

export type Band = {
  id: number;
  code: string;
  name: string;
  bandType: string;
  sppEnabled: number;
  geoCode: string | null;
  bandVaultPda: string | null;
};

export type BandMember = {
  id: number;
  bandId: number;
  musicianProfileId: number;
  roleInBand: string;
  joinedAt: string;
};

export type SppVariable = {
  id: number;
  code: string;
  name: string;
  weightBps: number;
};

export type SppCycle = {
  id: number;
  bandId: number;
  code: string;
  status: string;
  startedAt: string;
  closedAt: string | null;
  intendedInstruction: string;
};

export type SppMemberScore = {
  id: number;
  sppCycleId: number;
  musicianProfileId: number;
  attendancePoints: number;
  punctualityPoints: number;
  creativePoints: number;
  skillPoints: number;
  concertPoints: number;
  totalPoints: number;
  shareBps: number;
  enigmaLevelStart: number | null;
  enigmaLevelEnd: number | null;
};

export type RehearsalSession = {
  id: number;
  bandId: number;
  scheduledAt: string;
  notes: string | null;
};

export type RehearsalCheckin = {
  id: number;
  rehearsalSessionId: number;
  musicianProfileId: number;
  arrivedAt: string;
  lateMinutes: number;
  attendancePoints: number;
  punctualityPoints: number;
};

export async function fetchBands(): Promise<Band[]> {
  const { data } = await client.get<Band[]>("/api/bands");
  return data;
}

export async function createBand(input: {
  code: string;
  name: string;
  bandType?: string;
  sppEnabled?: number;
  geoCode?: string;
}): Promise<Band> {
  const { data } = await client.post<Band>("/api/bands", input);
  return data;
}

export async function fetchMembers(bandId: number): Promise<BandMember[]> {
  const { data } = await client.get<BandMember[]>(`/api/bands/${bandId}/members`);
  return data;
}

export async function addBandMember(input: {
  bandId: number;
  musicianProfileId: number;
  roleInBand?: string;
}): Promise<BandMember> {
  const { data } = await client.post<BandMember>("/api/band-members", input);
  return data;
}

export async function fetchSppVariables(): Promise<SppVariable[]> {
  const { data } = await client.get<SppVariable[]>("/api/spp-variables");
  return data;
}

export async function fetchCycles(bandId: number): Promise<SppCycle[]> {
  const { data } = await client.get<SppCycle[]>(`/api/bands/${bandId}/cycles`);
  return data;
}

export async function openCycle(input: {
  bandId: number;
  code: string;
  status?: string;
}): Promise<SppCycle> {
  const { data } = await client.post<SppCycle>("/api/spp-cycles", input);
  return data;
}

export async function fetchScores(cycleId: number): Promise<SppMemberScore[]> {
  const { data } = await client.get<SppMemberScore[]>(`/api/spp-cycles/${cycleId}/scores`);
  return data;
}

export async function closeCycle(cycleId: number): Promise<SppMemberScore[]> {
  const { data } = await client.post<SppMemberScore[]>(`/api/spp-cycles/${cycleId}/close`);
  return data;
}

export async function fetchRehearsals(bandId: number): Promise<RehearsalSession[]> {
  const { data } = await client.get<RehearsalSession[]>(`/api/bands/${bandId}/rehearsals`);
  return data;
}

export async function createRehearsalSession(input: {
  bandId: number;
  notes?: string;
}): Promise<RehearsalSession> {
  const { data } = await client.post<RehearsalSession>("/api/rehearsal-sessions", input);
  return data;
}

export async function checkInRehearsal(input: {
  rehearsalSessionId: number;
  musicianProfileId: number;
  lateMinutes?: number;
}): Promise<RehearsalCheckin> {
  const { data } = await client.post<RehearsalCheckin>("/api/rehearsal-checkins", input);
  return data;
}
