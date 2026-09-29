import { ensureRows, loadQuota } from "./db";
import { deviceKey } from "./device";
import { ApiError } from "./errors";
import { type ExtractFn, LlmUnavailable } from "./llm";
import { hasQuota, isPremium, limitOf, normalize, type QuotaLimits } from "./quota";
import { type ExtractData, trimForTier } from "./schema";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const MAX_CHARS = 20_000;
const CHARGE_TTL_MS = 40 * 24 * 60 * 60 * 1000;

export interface ExtractDeps {
  db: D1Database;
  extract: ExtractFn;
  salt: string;
  limits: QuotaLimits;
  now: number;
}

export interface ExtractResponse {
  data: ExtractData;
  tasks_total: number;
  quota: { used: number; limit: number };
}

export async function handleExtract(input: Record<string, unknown>, deps: ExtractDeps): Promise<ExtractResponse> {
  const { db, now, limits } = deps;
  const text = typeof input.ocr_text === "string" ? input.ocr_text.trim() : "";
  if (!text || text.length > MAX_CHARS) throw new ApiError("invalid-argument", "ocr_text");
  const itemId = input.item_id;
  if (typeof itemId !== "string" || !UUID.test(itemId)) throw new ApiError("invalid-argument", "item_id");
  const key = deviceKey(input.device_id, deps.salt);

  const before = normalize(await loadQuota(db, key), now);
  const premium = isPremium(before, now);
  // One charge per item per tier: a retry is free, re-processing after an upgrade costs one premium use.
  const chargeId = `${itemId}_${premium ? "p" : "f"}`;
  const charged = await db.prepare("SELECT 1 FROM charges WHERE device_key = ? AND charge_id = ?").bind(key, chargeId).first();
  if (!charged && !hasQuota(before, limits, now)) throw new ApiError("resource-exhausted", "quota");

  let data: ExtractData;
  try {
    data = await deps.extract(text);
  } catch (e) {
    if (e instanceof LlmUnavailable) throw new ApiError("unavailable", "llm");
    throw e;
  }

  // ponytail: pre-check and charge are separate, so parallel requests can overshoot the limit by a few uses.
  await db.batch([
    ...ensureRows(db, key, now),
    db.prepare("INSERT OR IGNORE INTO charges (device_key, charge_id, expire_at) VALUES (?, ?, ?)").bind(key, chargeId, now + CHARGE_TTL_MS),
    // changes() is the row count of the INSERT just above: 0 means this item was already charged.
    db.prepare("UPDATE quota SET used = used + 1 WHERE device_key = ? AND changes() = 1").bind(key),
  ]);
  const after = normalize(await loadQuota(db, key), now);
  return { ...trimForTier(data, premium), quota: { used: after.used, limit: limitOf(after, limits, now) } };
}
