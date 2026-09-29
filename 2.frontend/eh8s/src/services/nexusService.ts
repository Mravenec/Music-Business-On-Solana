import { apiClient as client } from "./http";

/** enigma_level row (JOOQ POJO JSON). */
export type EnigmaLevel = {
  id: number;
  levelNumber: number;
  name: string;
  durationNote: string | null;
  milestone: string | null;
};

/** enigma_evaluation row (JOOQ POJO JSON). Lists are newline-separated text. */
export type EnigmaEvaluation = {
  id: number;
  musicianProfileId: number;
  evaluatorAccountId: number | null;
  weekStart: string;
  score: number;
  notes: string | null;
  recordingUrl: string | null;
  rubricJson: string | null;
  strengths: string | null;
  errorsFound: string | null;
  exercises: string | null;
  recommendedLevel: number | null;
  lessonId: number | null;
  source: string;
  aiModel: string | null;
  appliedAt: string | null;
  createdAt: string;
};

export type NexusStatus = {
  aiConfigured: boolean;
  model: string;
  rubricKeys: string[];
  levels: EnigmaLevel[];
  audioNote: string;
};

/** enigma_evaluation POJO keys; rubricJson is the rubric object serialized to JSON. */
export type NexusRequest = {
  musicianProfileId: number;
  recordingUrl?: string;
  rubricJson: string;
  notes?: string;
  /** Lesson whose practice prompt is being answered; 400 when it has none. */
  lessonId?: number;
};

export type NexusResult = {
  evaluation: EnigmaEvaluation;
  currentLevel: number;
  recommendedLevel: number;
  levelChange: boolean;
  summary: string;
  audioNote: string;
};

export type NexusEvaluationDetail = {
  evaluation: EnigmaEvaluation;
  musician: { id: number; instrument: string | null; enigmaScore: number };
  currentLevel: number;
  levelChange: boolean;
  audioNote: string;
};

export async function fetchNexusStatus(): Promise<NexusStatus> {
  const { data } = await client.get<NexusStatus>("/api/nexus/status");
  return data;
}

/** Without a profile id the backend uses the signed-in musician's own profile. */
export async function fetchNexusEvaluations(musicianProfileId?: number): Promise<EnigmaEvaluation[]> {
  const { data } = await client.get<{ evaluation: EnigmaEvaluation }[]>("/api/nexus/evaluations", {
    params: musicianProfileId ? { musicianProfileId } : undefined,
  });
  return data.map((row) => row.evaluation);
}

export async function fetchNexusEvaluation(id: number): Promise<NexusEvaluationDetail> {
  const { data } = await client.get<NexusEvaluationDetail>(`/api/nexus/evaluations/${id}`);
  return data;
}

/** 503 when the server has no ANTHROPIC_API_KEY. */
export async function requestScoreEnigma(input: NexusRequest): Promise<NexusResult> {
  const { data } = await client.post<NexusResult>("/api/nexus/evaluations", input);
  return data;
}

export function lines(text: string | null): string[] {
  return (text ?? "")
    .split("\n")
    .map((s) => s.trim())
    .filter(Boolean);
}
