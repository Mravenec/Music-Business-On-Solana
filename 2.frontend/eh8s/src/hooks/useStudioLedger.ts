import { useEffect, useState } from "react";
import {
  fetchAcademyFees,
  fetchAllocations,
  fetchBandClaims,
  fetchInstructorShares,
  fetchMyAllocation,
  fetchPartners,
  fetchShowFees,
  fetchSoloClaims,
  fetchSyncFees,
  fetchVenueExpenses,
  deactivatePartner,
  savePartner,
  type AcademyFee,
  type InstructorShare,
  type PartnerAllocation,
  type ShowFee,
  type StudioClaim,
  type StudioPartner,
  type SyncFee,
  type VenueExpense,
} from "../services/studioLedgerService";

/**
 * Personal credits for one month: solo, band, venue, tutor, and this wallet's partner share.
 */
export function useStudioLedger(year: number, month: number) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [solo, setSolo] = useState<StudioClaim[]>([]);
  const [band, setBand] = useState<StudioClaim[]>([]);
  const [venue, setVenue] = useState<VenueExpense[]>([]);
  const [tutor, setTutor] = useState<InstructorShare[]>([]);
  const [partner, setPartner] = useState<PartnerAllocation[]>([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      fetchSoloClaims(year, month),
      fetchBandClaims(year, month),
      fetchVenueExpenses(year, month),
      fetchInstructorShares(year, month),
      fetchMyAllocation(year, month),
    ])
      .then(([soloRows, bandRows, venueRows, tutorRows, partnerRows]) => {
        if (cancelled) return;
        setSolo(soloRows);
        setBand(bandRows);
        setVenue(venueRows);
        setTutor(tutorRows);
        setPartner(partnerRows);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setSolo([]);
        setBand([]);
        setVenue([]);
        setTutor([]);
        setPartner([]);
        setError("Could not load studio earnings");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year, month]);

  return { loading, error, solo, band, venue, tutor, partner };
}

/**
 * Owner books for one month: partners, studio fee sources, and each partner's split.
 */
export function useOwnerLedger(year: number, month: number, reloadKey: number) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [partners, setPartners] = useState<StudioPartner[]>([]);
  const [shows, setShows] = useState<ShowFee[]>([]);
  const [academy, setAcademy] = useState<AcademyFee[]>([]);
  const [sync, setSync] = useState<SyncFee[]>([]);
  const [allocations, setAllocations] = useState<PartnerAllocation[]>([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      fetchPartners(),
      fetchShowFees(year, month),
      fetchAcademyFees(year, month),
      fetchSyncFees(year, month),
      fetchAllocations(year, month),
    ])
      .then(([partnerRows, showRows, academyRows, syncRows, allocationRows]) => {
        if (cancelled) return;
        setPartners(partnerRows);
        setShows(showRows);
        setAcademy(academyRows);
        setSync(syncRows);
        setAllocations(allocationRows);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError("Could not load the partner books");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year, month, reloadKey]);

  return {
    loading,
    error,
    partners,
    shows,
    academy,
    sync,
    allocations,
    savePartner,
    deactivatePartner,
  };
}
