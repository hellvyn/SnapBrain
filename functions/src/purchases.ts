import type { Firestore } from "firebase-admin/firestore";
import { deviceKey, sha256 } from "./device";
import { ApiError } from "./errors";
import { normalize } from "./quota";

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
  db: Firestore;
  play: PlayApi;
  salt: string;
  now: number;
}

// CANCELED keeps access until expiry. PENDING, ON_HOLD, PAUSED and EXPIRED (incl. refunds) get none.
const ENTITLED = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);

export const premiumUntilOf = (s: SubscriptionInfo): number | null => (ENTITLED.has(s.state) ? s.expiryMs : null);

const CLEARED = { premiumUntil: null, premiumToken: null };

/** Fields to write for this token's state, or null for "no change": an expired token never wipes premium granted by a newer one. */
function entitlementUpdate(
  quota: Record<string, unknown> | undefined,
  tokenHash: string,
  until: number | null,
): { premiumUntil: number | null; premiumToken: string | null } | null {
  if (until !== null) return { premiumUntil: until, premiumToken: tokenHash };
  return quota?.premiumToken === tokenHash ? CLEARED : null;
}

export async function handleVerifyPurchase(
  input: Record<string, unknown>,
  deps: PurchaseDeps,
): Promise<{ premium_until: number | null }> {
  const token = input.purchase_token;
  if (typeof token !== "string" || token.length === 0 || token.length > 4096) {
    throw new ApiError("invalid-argument", "purchase_token");
  }
  const key = deviceKey(input.device_id, deps.salt);
  let sub: SubscriptionInfo;
  try {
    sub = await deps.play.getSubscription(token);
  } catch {
    throw new ApiError("unavailable", "play");
  }
  const until = premiumUntilOf(sub);
  const { db, now } = deps;
  const tokenHash = sha256(token);
  const purchaseRef = db.doc(`purchases/${tokenHash}`);
  const quotaRef = db.doc(`quota/${key}`);

  await db.runTransaction(async (tx) => {
    const purchase = await tx.get(purchaseRef);
    const prevKey = purchase.get("deviceKey") as string | undefined;
    const prevRef = prevKey && prevKey !== key ? db.doc(`quota/${prevKey}`) : null;
    const [quota, prev] = await Promise.all([tx.get(quotaRef), prevRef ? tx.get(prevRef) : undefined]);
    // One token, one device: restoring on a new phone moves premium off the old one (only if it still holds this token).
    if (prevRef && prev?.get("premiumToken") === tokenHash) tx.set(prevRef, CLEARED, { merge: true });
    tx.set(quotaRef, { ...normalize(quota.data(), now), ...entitlementUpdate(quota.data(), tokenHash, until) });
    tx.set(purchaseRef, { deviceKey: key });
  });

  // Play refunds purchases left unacknowledged for 3 days; the app re-verifies on every launch, so a failure here retries.
  if (until !== null && !sub.acknowledged) await deps.play.acknowledge(sub.productId, token);
  return { premium_until: until };
}

export async function handleRtdn(
  message: unknown,
  deps: Pick<PurchaseDeps, "db" | "play">,
): Promise<"ignored" | "unknown" | "updated"> {
  const token = (message as { subscriptionNotification?: { purchaseToken?: string } } | null)
    ?.subscriptionNotification?.purchaseToken;
  if (!token) return "ignored";
  const tokenHash = sha256(token);
  const until = premiumUntilOf(await deps.play.getSubscription(token));
  const purchaseRef = deps.db.doc(`purchases/${tokenHash}`);
  return deps.db.runTransaction(async (tx) => {
    const purchase = await tx.get(purchaseRef);
    if (!purchase.exists) return "unknown";
    const quotaRef = deps.db.doc(`quota/${purchase.get("deviceKey")}`);
    const quota = await tx.get(quotaRef);
    const update = entitlementUpdate(quota.data(), tokenHash, until);
    if (update) tx.set(quotaRef, update, { merge: true });
    return "updated";
  });
}
