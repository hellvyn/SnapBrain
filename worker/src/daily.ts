import { entitlementStatements, type PlayApi, premiumUntilOf } from "./purchases";

const RECHECK_WINDOW_MS = 3 * 24 * 60 * 60 * 1000;

/** Replaces Play RTDN: drops expired idempotency rows, then re-reads recently active subscriptions once a day. */
export async function runDaily(deps: { db: D1Database; play: PlayApi | null; now: number }): Promise<{ rechecked: number; failed: number }> {
  const { db, play, now } = deps;
  // Cleanup first so a recheck failure or subrequest limit never starves it.
  await db.batch([
    db.prepare("DELETE FROM charges WHERE expire_at < ?").bind(now),
    db.prepare("DELETE FROM rewards WHERE expire_at < ?").bind(now),
  ]);
  let rechecked = 0;
  let failed = 0;
  if (play) {
    // ponytail: LIMIT 20 keeps us inside free-plan subrequest/D1 limits; raise on a paid plan or batch across runs.
    const { results } = await db
      .prepare("SELECT token, token_hash, device_key FROM purchases WHERE premium_until IS NOT NULL AND premium_until >= ? ORDER BY premium_until ASC LIMIT 20")
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
      } catch (e) {
        // Keep the current entitlement and retry tomorrow; log only the error class, never the token or message.
        failed++;
        console.error(JSON.stringify({ cron: "recheck", error: e instanceof Error ? e.name : "unknown" }));
      }
    }
  }
  return { rechecked, failed };
}
