import { apiClient as client } from "./http";
import type { Band } from "./bandService";

/** band_match_suggestion row (JOOQ POJO JSON). */
export type BandMatchSuggestion = {
  id: number;
  bandId: number;
  musicianProfileId: number;
  score: number;
  instrumentPoints: number;
  levelPoints: number;
  countryPoints: number;
  genrePoints: number;
  reason: string;
  aiRationale: string | null;
  status: string;
  createdAt: string;
};

export type MatchRow = {
  suggestion: BandMatchSuggestion;
  instrument: string | null;
  level: number | null;
  countryCode: string | null;
  genres: string | null;
};

export type MatchSuggestions = {
  band: Band;
  aiConfigured: boolean;
  suggestions: MatchRow[];
  aiUsed?: boolean;
  aiError?: string | null;
};

export async function fetchMatchSuggestions(bandId: number): Promise<MatchSuggestions> {
  const { data } = await client.get<MatchSuggestions>(`/api/bands/${bandId}/match-suggestions`);
  return data;
}

/** Recomputes the deterministic ranking; withAi adds a Claude sentence to the top five. */
export async function runHarmony(bandId: number, withAi: boolean): Promise<MatchSuggestions> {
  const { data } = await client.post<MatchSuggestions>(`/api/bands/${bandId}/match-suggestions`, {
    withAi,
  });
  return data;
}
