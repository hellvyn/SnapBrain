import { jakartaDate, monthOf, type QuotaDoc } from "./quota";

interface QuotaRow {
  month: string;
  used: number;
  bonus: number;
  rewards_day: string;
  rewards_today: number;
  premium_until: number | null;
  premium_token: string | null;
}

export async function loadQuota(db: D1Database, key: string): Promise<Partial<QuotaDoc> | undefined> {
  const row = await db.prepare("SELECT * FROM quota WHERE device_key = ?").bind(key).first<QuotaRow>();
  if (!row) return undefined;
  return {
    month: row.month,
    used: row.used,
    bonus: row.bonus,
    rewardsDay: row.rewards_day,
    rewardsToday: row.rewards_today,
    premiumUntil: row.premium_until,
    premiumToken: row.premium_token,
  };
}

/** Creates the quota row if missing and applies the Jakarta month/day rollovers, as statements for a batch. */
export function ensureRows(db: D1Database, key: string, now: number): D1PreparedStatement[] {
  const month = monthOf(now);
  const day = jakartaDate(now);
  return [
    db.prepare("INSERT OR IGNORE INTO quota (device_key, month, rewards_day) VALUES (?1, ?2, ?3)").bind(key, month, day),
    db.prepare("UPDATE quota SET month = ?2, used = 0, bonus = 0 WHERE device_key = ?1 AND month <> ?2").bind(key, month),
    db.prepare("UPDATE quota SET rewards_day = ?2, rewards_today = 0 WHERE device_key = ?1 AND rewards_day <> ?2").bind(key, day),
  ];
}
