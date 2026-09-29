import { entitlementStatements, type PlayApi, premiumUntilOf } from "./purchases";

const RECHECK_WINDOW_MS = 3 * 24 * 60 * 60 * 1000;

/** Replaces Play RTDN: re-reads every recently active subscription once a day, then drops expired idempotency rows. */
export async function runDaily(deps: { db: D1Database; play: PlayApi | null; now: number }): Promise<{ rechecked: number }> {
  const { db, play, now } = deps;
  let rechecked = 0;
  if (play) {
    const { results } = await db
      .prepare("SELECT token, token_hash, device_key FROM purchases WHERE premium_until IS NOT NULL AND premium_until >= ?")
      .bind(now - RECHECK_WINDOW_MS)
      .all<{ token: string; token_hash: string; device_key: string }>();
    for (const p of results) {
      try {
        const until = premiumUntilOf(await play.getSubscription(p.token));
        await db.batch([
          ...entitlementStatements(db, p.device_key, p.token_hash, until),
          db.prepare("UPDATE purchases SET premium_until = ? WHERE token_hash = ?").bind(until, p.token_hash),
        ]);
        rechecked++;
      } catch {
        // Play error for one token: keep the current entitlement and retry tomorrow.
      }
    }
  }
  await db.batch([
    db.prepare("DELETE FROM charges WHERE expire_at < ?").bind(now),
    db.prepare("DELETE FROM rewards WHERE expire_at < ?").bind(now),
  ]);
  return { rechecked };
}
