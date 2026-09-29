import { apiClient as client } from "./http";

/** creative_rating row (JOOQ POJO JSON). */
export type CreativeRating = {
  id: number;
  rehearsalSessionId: number;
  raterProfileId: number;
  rateeProfileId: number;
  score: number;
  createdAt: string;
};

/** concert_set_checkin row (JOOQ POJO JSON). */
export type ConcertSetCheckin = {
  id: number;
  concertId: number;
  musicianProfileId: number;
  setStartedAt: string;
  setEndedAt: string | null;
  minutesPlayed: number | null;
};

export async function fetchCreativeRatings(sessionId: number): Promise<CreativeRating[]> {
  const { data } = await client.get<CreativeRating[]>(
    `/api/rehearsal-sessions/${sessionId}/creative-ratings`
  );
  return data;
}

/** The rater is the signed-in musician (JWT); only the ratee and score are sent. */
export async function rateBandmate(
  sessionId: number,
  input: { rateeProfileId: number; score: number }
): Promise<CreativeRating> {
  const { data } = await client.post<CreativeRating>(
    `/api/rehearsal-sessions/${sessionId}/creative-ratings`,
    input
  );
  return data;
}

export async function fetchSetCheckins(concertId: number): Promise<ConcertSetCheckin[]> {
  const { data } = await client.get<ConcertSetCheckin[]>(`/api/concerts/${concertId}/set-checkins`);
  return data;
}

export async function startSet(concertId: number): Promise<ConcertSetCheckin> {
  const { data } = await client.post<ConcertSetCheckin>(
    `/api/concerts/${concertId}/set-checkins/start`,
    {}
  );
  return data;
}

export async function endSet(concertId: number): Promise<ConcertSetCheckin> {
  const { data } = await client.post<ConcertSetCheckin>(
    `/api/concerts/${concertId}/set-checkins/end`,
    {}
  );
  return data;
}
