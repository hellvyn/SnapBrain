import { verify } from "node:crypto";
import type { Firestore } from "firebase-admin/firestore";
import { loadConfig } from "./config";
import { deviceKey, sha256 } from "./device";
import { ApiError } from "./errors";
import { applyReward, normalize } from "./quota";

export type KeyFetcher = () => Promise<Map<string, string>>;
export type RewardResult = "granted" | "duplicate" | "limit";

export interface RewardDeps {
  db: Firestore;
  salt: string;
  getKeys: KeyFetcher;
  now: number;
}

const REWARD_TTL_MS = 40 * 24 * 60 * 60 * 1000;

/** AdMob SSV: the signed message is the raw query string before `&signature=`; signature and key_id come last. */
export async function verifySsv(rawQuery: string, getKeys: KeyFetcher): Promise<URLSearchParams> {
  const cut = rawQuery.indexOf("&signature=");
  const params = new URLSearchParams(rawQuery);
  const sig = params.get("signature");
  const pem = (await getKeys()).get(params.get("key_id") ?? "");
  let valid = false;
  try {
    valid = cut > 0 && sig !== null && pem !== undefined &&
      verify("sha256", Buffer.from(rawQuery.slice(0, cut)), pem, Buffer.from(sig, "base64url"));
  } catch {
    valid = false; // malformed DER signature
  }
  if (!valid) throw new ApiError("invalid-argument", "signature");
  return params;
}

const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
let keyCache: { at: number; keys: Map<string, string> } | null = null;

// ponytail: 24h cache, so a rotated key fails until expiry (AdMob retries callbacks); refetch on unknown key_id if that bites.
export const fetchAdmobKeys: KeyFetcher = async () => {
  if (keyCache && Date.now() - keyCache.at < 24 * 60 * 60 * 1000) return keyCache.keys;
  const res = await fetch(KEYS_URL);
  if (!res.ok) throw new Error(`admob keys ${res.status}`);
  const body = (await res.json()) as { keys: { keyId: number; pem: string }[] };
  keyCache = { at: Date.now(), keys: new Map(body.keys.map((k) => [String(k.keyId), k.pem])) };
  return keyCache.keys;
};

export async function handleReward(rawQuery: string, deps: RewardDeps): Promise<RewardResult> {
  const params = await verifySsv(rawQuery, deps.getKeys);
  const txId = params.get("transaction_id");
  if (!txId) throw new ApiError("invalid-argument", "transaction_id");
  const key = deviceKey(params.get("custom_data"), deps.salt);
  const cfg = await loadConfig(deps.db, deps.now);
  const rewardRef = deps.db.doc(`rewards/${sha256(txId)}`);
  const quotaRef = deps.db.doc(`quota/${key}`);

  return deps.db.runTransaction(async (tx): Promise<RewardResult> => {
    const [reward, quota] = await Promise.all([tx.get(rewardRef), tx.get(quotaRef)]);
    if (reward.exists) return "duplicate";
    const next = applyReward(normalize(quota.data(), deps.now), cfg);
    tx.set(rewardRef, { granted: next !== null, expireAt: new Date(deps.now + REWARD_TTL_MS) });
    if (!next) return "limit";
    tx.set(quotaRef, next);
    return "granted";
  });
}
