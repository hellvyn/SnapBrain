import { ensureRows } from "./db";
import { deviceKey, sha256 } from "./device";
import { ApiError } from "./errors";
import type { QuotaLimits } from "./quota";

export type KeyFetcher = (refresh?: boolean) => Promise<Map<string, string>>;
export type RewardResult = "granted" | "duplicate" | "limit";

export interface RewardDeps {
  db: D1Database;
  salt: string;
  limits: QuotaLimits;
  getKeys: KeyFetcher;
  now: number;
  adUnitId: string;
}

const REWARD_TTL_MS = 40 * 24 * 60 * 60 * 1000;

const base64ToBytes = (b64: string): Uint8Array => {
  const s = b64.replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(s + "=".repeat((4 - (s.length % 4)) % 4)), (c) => c.charCodeAt(0));
};
const pemToSpki = (pem: string): Uint8Array => base64ToBytes(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""));

/** DER ECDSA signature (what AdMob sends) → IEEE P1363 r||s (what WebCrypto verifies). */
export function derToP1363(der: Uint8Array): Uint8Array {
  let i = 0;
  if (der[i++] !== 0x30) throw new Error("not DER");
  i += der[i] & 0x80 ? 1 + (der[i] & 0x7f) : 1;
  const readInt = (): Uint8Array => {
    if (der[i++] !== 0x02) throw new Error("not INTEGER");
    const len = der[i++];
    let v = der.slice(i, i + len);
    i += len;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    if (v.length > 32) throw new Error("integer too long");
    const out = new Uint8Array(32);
    out.set(v, 32 - v.length);
    return out;
  };
  const r = readInt();
  const s = readInt();
  const sig = new Uint8Array(64);
  sig.set(r, 0);
  sig.set(s, 32);
  return sig;
}

/** AdMob SSV: the signed message is the raw query string before `&signature=`; signature and key_id come last. */
export async function verifySsv(rawQuery: string, getKeys: KeyFetcher): Promise<URLSearchParams> {
  const cut = rawQuery.indexOf("&signature=");
  const tail = new URLSearchParams(rawQuery);
  const sig = tail.get("signature");
  const keyId = tail.get("key_id") ?? "";
  let keys = await getKeys();
  if (!keys.has(keyId)) keys = await getKeys(true); // unknown key: maybe rotated
  const pem = keys.get(keyId);
  let valid = false;
  try {
    if (cut > 0 && sig && pem) {
      const key = await crypto.subtle.importKey("spki", pemToSpki(pem), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
      valid = await crypto.subtle.verify(
        { name: "ECDSA", hash: "SHA-256" },
        key,
        derToP1363(base64ToBytes(sig)),
        new TextEncoder().encode(rawQuery.slice(0, cut)),
      );
    }
  } catch {
    valid = false; // malformed DER or key
  }
  if (!valid) throw new ApiError("invalid-argument", "signature");
  // Only the signed part is trusted; anything appended after the signature is ignored.
  return new URLSearchParams(rawQuery.slice(0, cut));
}

const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
let keyCache: { at: number; keys: Map<string, string> } | null = null;

// 24h cache; an unknown key_id refetches once the cache is over 5 minutes old (rotation).
export const fetchAdmobKeys: KeyFetcher = async (refresh) => {
  const age = keyCache ? Date.now() - keyCache.at : Infinity;
  if (keyCache && age < 24 * 60 * 60 * 1000 && !(refresh && age >= 5 * 60 * 1000)) return keyCache.keys;
  const res = await fetch(KEYS_URL, { signal: AbortSignal.timeout(5_000) });
  if (!res.ok) throw new Error(`admob keys ${res.status}`);
  const body = (await res.json()) as { keys: { keyId: number; pem: string }[] };
  keyCache = { at: Date.now(), keys: new Map(body.keys.map((k) => [String(k.keyId), k.pem])) };
  return keyCache.keys;
};

export async function handleReward(rawQuery: string, deps: RewardDeps): Promise<RewardResult> {
  const params = await verifySsv(rawQuery, deps.getKeys);
  if (!deps.adUnitId || params.get("ad_unit") !== deps.adUnitId) throw new ApiError("invalid-argument", "ad_unit");
  const txId = params.get("transaction_id");
  if (!txId) throw new ApiError("invalid-argument", "transaction_id");
  const key = deviceKey(params.get("custom_data"), deps.salt);
  const { db, now, limits } = deps;
  const txHash = sha256(txId);

  const results = await db.batch([
    ...ensureRows(db, key, now),
    db.prepare("INSERT OR IGNORE INTO rewards (tx_hash, granted, expire_at) VALUES (?, 0, ?)").bind(txHash, now + REWARD_TTL_MS),
    db.prepare("UPDATE quota SET bonus = bonus + ?2, rewards_today = rewards_today + 1 WHERE device_key = ?1 AND changes() = 1 AND rewards_today < ?3")
      .bind(key, limits.rewardAmount, limits.rewardMaxPerDay),
    db.prepare("UPDATE rewards SET granted = 1 WHERE tx_hash = ? AND changes() = 1").bind(txHash),
  ]);
  const inserted = results[3].meta.changes;
  const granted = results[4].meta.changes;
  if (inserted === 0) return "duplicate";
  return granted === 1 ? "granted" : "limit";
}
