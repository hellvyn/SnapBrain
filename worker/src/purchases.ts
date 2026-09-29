import { ensureRows } from "./db";
import { deviceKey, sha256 } from "./device";
import { ApiError } from "./errors";

export interface SubscriptionInfo {
  state: string;
  expiryMs: number | null;
  acknowledged: boolean;
  productId: string;
}

export interface PlayApi {
  getSubscription(token: string): Promise<SubscriptionInfo>;
  acknowledge(productId: string, token: string): Promise<void>;
}

export interface PurchaseDeps {
  db: D1Database;
  play: PlayApi | null;
  salt: string;
  now: number;
}

// CANCELED keeps access until expiry. PENDING, ON_HOLD, PAUSED and EXPIRED (incl. refunds) get none.
const ENTITLED = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);
export function premiumUntilOf(s: SubscriptionInfo): number | null {
  if (!ENTITLED.has(s.state)) return null;
  // A malformed response must not revoke: callers treat the throw as a Play failure and keep the entitlement.
  if (s.expiryMs === null) throw new Error("entitled subscription without expiry");
  return s.expiryMs;
}

/** Grant sets premium + owner token unless another token holds a longer grant; a non-entitled result only clears premium this token granted. */
export function entitlementStatements(db: D1Database, key: string, tokenHash: string, until: number | null): D1PreparedStatement[] {
  return until !== null
    ? [db.prepare("UPDATE quota SET premium_until = ?2, premium_token = ?3 WHERE device_key = ?1 AND (premium_token = ?3 OR premium_token IS NULL OR premium_until IS NULL OR premium_until < ?2)").bind(key, until, tokenHash)]
    : [db.prepare("UPDATE quota SET premium_until = NULL, premium_token = NULL WHERE device_key = ?1 AND premium_token = ?2").bind(key, tokenHash)];
}

export async function handleVerifyPurchase(
  input: Record<string, unknown>,
  deps: PurchaseDeps,
): Promise<{ premium_until: number | null }> {
  const token = input.purchase_token;
  if (typeof token !== "string" || token.length === 0 || token.length > 4096) throw new ApiError("invalid-argument", "purchase_token");
  const key = deviceKey(input.device_id, deps.salt);
  if (!deps.play) throw new ApiError("unavailable", "play not configured");
  let sub: SubscriptionInfo;
  let until: number | null;
  try {
    sub = await deps.play.getSubscription(token);
    until = premiumUntilOf(sub);
  } catch {
    throw new ApiError("unavailable", "play");
  }
  const { db, now } = deps;
  const tokenHash = sha256(token);

  await db.batch([
    ...ensureRows(db, key, now),
    // One token, one device: restoring on a new phone moves premium off whichever device held it.
    db.prepare("UPDATE quota SET premium_until = NULL, premium_token = NULL WHERE premium_token = ?1 AND device_key <> ?2").bind(tokenHash, key),
    ...entitlementStatements(db, key, tokenHash, until),
    db.prepare(`INSERT INTO purchases (token_hash, token, device_key, premium_until) VALUES (?1, ?2, ?3, ?4)
                ON CONFLICT(token_hash) DO UPDATE SET device_key = excluded.device_key, premium_until = excluded.premium_until`)
      .bind(tokenHash, token, key, until),
  ]);

  // Play refunds purchases left unacknowledged for 3 days; the app re-verifies on launch, so a failure here retries.
  if (until !== null && !sub.acknowledged) await deps.play.acknowledge(sub.productId, token);
  return { premium_until: until };
}
