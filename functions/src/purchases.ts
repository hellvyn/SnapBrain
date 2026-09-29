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
  const purchaseRef = db.doc(`purchases/${sha256(token)}`);
  const quotaRef = db.doc(`quota/${key}`);

  await db.runTransaction(async (tx) => {
    const [purchase, quota] = await Promise.all([tx.get(purchaseRef), tx.get(quotaRef)]);
    const prevKey = purchase.get("deviceKey") as string | undefined;
    // One token, one device: restoring on a new phone moves premium off the old one.
    if (prevKey && prevKey !== key) tx.set(db.doc(`quota/${prevKey}`), { premiumUntil: null }, { merge: true });
    tx.set(quotaRef, { ...normalize(quota.data(), now), premiumUntil: until });
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
  const purchase = await deps.db.doc(`purchases/${sha256(token)}`).get();
  if (!purchase.exists) return "unknown";
  const until = premiumUntilOf(await deps.play.getSubscription(token));
  await deps.db.doc(`quota/${purchase.get("deviceKey")}`).set({ premiumUntil: until }, { merge: true });
  return "updated";
}
