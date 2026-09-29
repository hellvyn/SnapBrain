import { ApiError } from "./errors";

/**
 * Beta mode (APP_CHECK_MODE=optional): requests without a valid App Check token share one daily budget, so an
 * app-less script cannot drain the LLM. Counted before the LLM call, so failed attempts count too.
 */
export async function takeUnverifiedSlot(db: D1Database, now: number, cap: number): Promise<void> {
  const day = new Date(now).toISOString().slice(0, 10);
  const row = await db
    .prepare("INSERT INTO unverified_daily (day, count) VALUES (?, 1) ON CONFLICT(day) DO UPDATE SET count = count + 1 RETURNING count")
    .bind(day)
    .first<{ count: number }>();
  if ((row?.count ?? cap + 1) > cap) throw new ApiError("resource-exhausted", "beta daily cap");
}
