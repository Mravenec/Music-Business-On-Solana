import { useCallback, useEffect, useRef, useState } from "react";
import {
  addBandMember,
  checkInRehearsal,
  closeCycle,
  createBand,
  createRehearsalSession,
  fetchBands,
  fetchCycles,
  fetchMembers,
  fetchRehearsals,
  fetchScores,
  fetchSppVariables,
  openCycle,
  type Band,
  type BandMember,
  type RehearsalSession,
  type SppCycle,
  type SppMemberScore,
  type SppVariable,
} from "../services/bandService";
import { useSession } from "./useSession";

export type BandsState = {
  loading: boolean;
  error: string | null;
  bands: Band[];
  members: BandMember[];
  variables: SppVariable[];
  cycles: SppCycle[];
  scores: SppMemberScore[];
  rehearsals: RehearsalSession[];
  selectedBandId: number | null;
  setSelectedBandId: (id: number | null) => void;
  refresh: () => Promise<void>;
  createBandRow: (input: {
    code: string;
    name: string;
    bandType?: string;
    sppEnabled?: number;
    geoCode?: string;
  }) => Promise<Band>;
  addMember: (input: {
    bandId: number;
    musicianProfileId: number;
    roleInBand?: string;
  }) => Promise<void>;
  createSession: (input: { bandId: number; notes?: string }) => Promise<RehearsalSession>;
  checkIn: (input: {
    rehearsalSessionId: number;
    musicianProfileId: number;
    lateMinutes?: number;
  }) => Promise<void>;
  openSppCycle: (input: { bandId: number; code: string }) => Promise<void>;
  closeSppCycle: (cycleId: number) => Promise<void>;
};

/**
 * Live bands / SPP data and product-console mutations (MariaDB via Spring).
 */
export function useBands(): BandsState {
  const { session } = useSession();
  const profileId = session?.musicianProfile?.id ?? null;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bands, setBands] = useState<Band[]>([]);
  const [members, setMembers] = useState<BandMember[]>([]);
  const [variables, setVariables] = useState<SppVariable[]>([]);
  const [cycles, setCycles] = useState<SppCycle[]>([]);
  const [scores, setScores] = useState<SppMemberScore[]>([]);
  const [rehearsals, setRehearsals] = useState<RehearsalSession[]>([]);
  const [selectedBandId, setSelectedBandId] = useState<number | null>(null);
  const selectedRef = useRef(selectedBandId);
  selectedRef.current = selectedBandId;

  const refresh = useCallback(async () => {
    const requested = selectedBandId;
    setLoading(true);
    try {
      const [bandRows, variableRows] = await Promise.all([
        fetchBands(),
        fetchSppVariables(),
      ]);
      const memberRows = requested != null ? await fetchMembers(requested) : [];
      const cycleRows = requested != null ? await fetchCycles(requested) : [];
      const rehearsalRows = requested != null ? await fetchRehearsals(requested) : [];
      const openOrFirst =
        cycleRows.find((c) => c.status === "open") ?? cycleRows[cycleRows.length - 1] ?? null;
      const scoreRows = openOrFirst ? await fetchScores(openOrFirst.id) : [];
      if (selectedRef.current !== requested) return;
      setBands(bandRows);
      setVariables(variableRows);
      setMembers(memberRows);
      setCycles(cycleRows);
      setScores(scoreRows);
      setRehearsals(rehearsalRows);
      setError(null);
    } catch (err: unknown) {
      if (selectedRef.current !== requested) return;
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: string }).message)
          : "Network Error";
      setError(message);
    } finally {
      if (selectedRef.current === requested) setLoading(false);
    }
  }, [selectedBandId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    bands,
    members,
    variables,
    cycles,
    scores,
    rehearsals,
    selectedBandId,
    setSelectedBandId,
    refresh,
    createBandRow: async (input) => {
      if (profileId == null) {
        throw new Error("Open a musician profile before creating a band");
      }
      const row = await createBand(input);
      await addBandMember({
        bandId: row.id,
        musicianProfileId: profileId,
        roleInBand: "leader",
      });
      setSelectedBandId(row.id);
      return row;
    },
    addMember: async (input) => {
      await addBandMember(input);
      await refresh();
    },
    createSession: async (input) => {
      const row = await createRehearsalSession(input);
      await refresh();
      return row;
    },
    checkIn: async (input) => {
      await checkInRehearsal(input);
      await refresh();
    },
    openSppCycle: async (input) => {
      await openCycle(input);
      await refresh();
    },
    closeSppCycle: async (cycleId) => {
      await closeCycle(cycleId);
      await refresh();
    },
  };
}
