import { useCallback, useEffect, useState } from "react";
import {
  createChannelPiece as apiCreatePiece,
  createTrack as apiCreateTrack,
  createTourPlan as apiCreateTour,
  depositRoyalty as apiDeposit,
  fetchChannelPieces,
  fetchGeoRegions,
  fetchGeoSubscriptions,
  fetchGeoTiers,
  fetchRoyaltyDeposits,
  fetchSplits,
  fetchTourPlans,
  fetchTracks,
  subscribeGeo as apiSubscribeGeo,
  type ChannelPiece,
  type GeoRegion,
  type GeoTier,
  type GeographicSubscription,
  type RoyaltyDeposit,
  type RoyaltySplit,
  type TourPlan,
  type Track,
} from "../services/catalogService";

export type CatalogState = {
  loading: boolean;
  error: string | null;
  tracks: Track[];
  splits: RoyaltySplit[];
  geoTiers: GeoTier[];
  geoRegions: GeoRegion[];
  geoSubs: GeographicSubscription[];
  tours: TourPlan[];
  pieces: ChannelPiece[];
  deposits: RoyaltyDeposit[];
  refresh: () => Promise<void>;
  depositRoyalty: (input: {
    trackId: number;
    royaltyTypeId: number;
    amountUsdc: number;
  }) => Promise<RoyaltyDeposit>;
  subscribeGeo: (input: {
    bandId: number;
    geoTierId: number;
    geoRegionId: number;
    geoCode?: string;
    months?: number;
  }) => Promise<GeographicSubscription>;
  createTour: (input: {
    bandId: number;
    geoRegionId: number;
    title: string;
    routeNote?: string;
  }) => Promise<void>;
  createPiece: (input: { channelFormatId: number; title: string }) => Promise<void>;
  createTrackRow: (input: {
    title: string;
    provider?: string;
    bandId?: number;
  }) => Promise<Track>;
};

/**
 * Live catalog / geo / channel CMS state.
 */
export function useCatalog(): CatalogState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [splits, setSplits] = useState<RoyaltySplit[]>([]);
  const [geoTiers, setGeoTiers] = useState<GeoTier[]>([]);
  const [geoRegions, setGeoRegions] = useState<GeoRegion[]>([]);
  const [geoSubs, setGeoSubs] = useState<GeographicSubscription[]>([]);
  const [tours, setTours] = useState<TourPlan[]>([]);
  const [pieces, setPieces] = useState<ChannelPiece[]>([]);
  const [deposits, setDeposits] = useState<RoyaltyDeposit[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [t, tiers, regions, subs, tourRows, pieceRows, depositRows] = await Promise.all([
        fetchTracks(),
        fetchGeoTiers(),
        fetchGeoRegions(),
        fetchGeoSubscriptions(),
        fetchTourPlans(),
        fetchChannelPieces(),
        fetchRoyaltyDeposits(),
      ]);
      const splitRows = t[0] ? await fetchSplits(t[0].id) : [];
      setTracks(t);
      setSplits(splitRows);
      setGeoTiers(tiers);
      setGeoRegions(regions);
      setGeoSubs(subs);
      setTours(tourRows);
      setPieces(pieceRows);
      setDeposits(depositRows);
      setError(null);
    } catch (err: unknown) {
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: string }).message)
          : "Network Error";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    tracks,
    splits,
    geoTiers,
    geoRegions,
    geoSubs,
    tours,
    pieces,
    deposits,
    refresh,
    depositRoyalty: async (input) => {
      const row = await apiDeposit(input);
      await refresh();
      return row;
    },
    subscribeGeo: async (input) => {
      const row = await apiSubscribeGeo(input);
      await refresh();
      return row;
    },
    createTour: async (input) => {
      await apiCreateTour(input);
      await refresh();
    },
    createPiece: async (input) => {
      await apiCreatePiece(input);
      await refresh();
    },
    createTrackRow: async (input) => {
      const row = await apiCreateTrack(input);
      await refresh();
      return row;
    },
  };
}
