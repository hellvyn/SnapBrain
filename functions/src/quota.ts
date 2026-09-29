export interface QuotaDoc {
  month: string;
  used: number;
  bonus: number;
  rewardsDay: string;
  rewardsToday: number;
  premiumUntil: number | null;
  /** sha256 of the purchase token that granted premium; owned by purchases.ts. */
  premiumToken?: string | null;
}

export interface QuotaLimits {
  limitFree: number;
  limitPremium: number;
  rewardAmount: number;
  rewardMaxPerDay: number;
}

const WIB_OFFSET_MS = 7 * 60 * 60 * 1000; // Asia/Jakarta is UTC+7 with no DST

export const jakartaDate = (now: number): string => new Date(now + WIB_OFFSET_MS).toISOString().slice(0, 10);
export const monthOf = (now: number): string => jakartaDate(now).slice(0, 7);

export function normalize(doc: Partial<QuotaDoc> | undefined, now: number): QuotaDoc {
  const month = monthOf(now);
  const day = jakartaDate(now);
  const d: QuotaDoc = { month, used: 0, bonus: 0, rewardsDay: day, rewardsToday: 0, premiumUntil: null, ...doc };
  if (d.month !== month) Object.assign(d, { month, used: 0, bonus: 0 });
  if (d.rewardsDay !== day) Object.assign(d, { rewardsDay: day, rewardsToday: 0 });
  return d;
}

export const isPremium = (d: QuotaDoc, now: number): boolean => d.premiumUntil !== null && d.premiumUntil > now;

export const limitOf = (d: QuotaDoc, l: QuotaLimits, now: number): number =>
  (isPremium(d, now) ? l.limitPremium : l.limitFree) + d.bonus;

export const hasQuota = (d: QuotaDoc, l: QuotaLimits, now: number): boolean => d.used < limitOf(d, l, now);

export function applyReward(d: QuotaDoc, l: QuotaLimits): QuotaDoc | null {
  if (d.rewardsToday >= l.rewardMaxPerDay) return null;
  return { ...d, bonus: d.bonus + l.rewardAmount, rewardsToday: d.rewardsToday + 1 };
}
