import type { Firestore } from "firebase-admin/firestore";
import { loadConfig } from "./config";
import { deviceKey } from "./device";
import { ApiError } from "./errors";
import { type ExtractFn, LlmUnavailable } from "./llm";
import { hasQuota, isPremium, limitOf, normalize } from "./quota";
import { type ExtractData, trimForTier } from "./schema";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const MAX_CHARS = 20_000;
const CHARGE_TTL_MS = 40 * 24 * 60 * 60 * 1000;

export interface ExtractDeps {
  db: Firestore;
  extract: ExtractFn;
  salt: string;
  now: number;
}

export interface ExtractResponse {
  data: ExtractData;
  tasks_total: number;
  quota: { used: number; limit: number };
}

export async function handleExtract(input: Record<string, unknown>, deps: ExtractDeps): Promise<ExtractResponse> {
  const { db, now } = deps;
  const text = typeof input.ocr_text === "string" ? input.ocr_text.trim() : "";
  if (!text || text.length > MAX_CHARS) throw new ApiError("invalid-argument", "ocr_text");
  const itemId = input.item_id;
  if (typeof itemId !== "string" || !UUID.test(itemId)) throw new ApiError("invalid-argument", "item_id");
  const key = deviceKey(input.device_id, deps.salt);
  const cfg = await loadConfig(db, now);

  const quotaRef = db.doc(`quota/${key}`);
  const before = normalize((await quotaRef.get()).data(), now);
  const premium = isPremium(before, now);
  // One charge per item per tier: a retry of the same item is free,
  // re-processing a locked item after upgrading costs one premium use (spec §8).
  const chargeRef = quotaRef.collection("charges").doc(`${itemId}_${premium ? "p" : "f"}`);
  if (!(await chargeRef.get()).exists && !hasQuota(before, cfg, now)) {
    throw new ApiError("resource-exhausted", "quota");
  }

  let data: ExtractData;
  try {
    data = await deps.extract(text, cfg);
  } catch (e) {
    if (e instanceof LlmUnavailable) throw new ApiError("unavailable", "llm");
    throw e;
  }

  // ponytail: the quota pre-check and the charge are separate, so parallel requests can overshoot
  // the limit by a few uses; reserve inside a transaction before the LLM call if abuse shows up.
  const quota = await db.runTransaction(async (tx) => {
    const d = normalize((await tx.get(quotaRef)).data(), now);
    if (!(await tx.get(chargeRef)).exists) {
      d.used += 1;
      tx.set(quotaRef, d);
      tx.set(chargeRef, { expireAt: new Date(now + CHARGE_TTL_MS) });
    }
    return { used: d.used, limit: limitOf(d, cfg, now) };
  });

  return { ...trimForTier(data, premium), quota };
}
