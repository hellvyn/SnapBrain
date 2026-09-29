import type { Firestore } from "firebase-admin/firestore";
import type { LlmConfig } from "./llm";
import type { QuotaLimits } from "./quota";

export type AppConfig = QuotaLimits & LlmConfig;

export const DEFAULT_CONFIG: AppConfig = {
  limitFree: 15,
  limitPremium: 300,
  rewardAmount: 3,
  rewardMaxPerDay: 3,
  llmModel: "claude-opus-5-5",
  llmEffort: "low",
};

const TTL_MS = 60_000;
let cached: { at: number; cfg: AppConfig } | null = null;

/** Firestore doc config/app overrides defaults; edit it in the Console, no deploy needed. */
export async function loadConfig(db: Firestore, now: number): Promise<AppConfig> {
  if (cached && now - cached.at < TTL_MS) return cached.cfg;
  const snap = await db.doc("config/app").get();
  const cfg: AppConfig = { ...DEFAULT_CONFIG, ...(snap.data() as Partial<AppConfig> | undefined) };
  cached = { at: now, cfg };
  return cfg;
}

export function resetConfigCache(): void {
  cached = null;
}
