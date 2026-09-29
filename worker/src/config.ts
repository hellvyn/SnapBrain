import type { QuotaLimits } from "./quota";

export interface Env {
  DB: D1Database;
  LIMIT_FREE: string;
  LIMIT_PREMIUM: string;
  REWARD_AMOUNT: string;
  REWARD_MAX_PER_DAY: string;
  LLM_BASE_URL: string;
  LLM_MODEL: string;
  LLM_API_KEY?: string;
  DEVICE_SALT?: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  PACKAGE_NAME: string;
  ADMOB_AD_UNIT_ID: string;
  PLAY_SERVICE_ACCOUNT_JSON?: string;
}

export const limitsOf = (env: Env): QuotaLimits => ({
  limitFree: Number(env.LIMIT_FREE),
  limitPremium: Number(env.LIMIT_PREMIUM),
  rewardAmount: Number(env.REWARD_AMOUNT),
  rewardMaxPerDay: Number(env.REWARD_MAX_PER_DAY),
});
