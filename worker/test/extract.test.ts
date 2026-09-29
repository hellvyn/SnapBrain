import { env } from "cloudflare:test";
import { describe, expect, it, vi } from "vitest";
import { deviceKey } from "../src/device";
import { handleExtract } from "../src/extract";
import { LlmUnavailable } from "../src/llm";
import type { ExtractData } from "../src/schema";
import { DAY, newDeviceId, NOW, SALT } from "./helpers";

const LIMITS = { limitFree: 15, limitPremium: 300, rewardAmount: 3, rewardMaxPerDay: 3 };
const DATA: ExtractData = {
  category: "task",
  title: "Tugas kelompok",
  extracted_info: {},
  action_type: "none",
  action_payload: "",
  tasks: [1, 2, 3].map((id) => ({ id, description: `Langkah ${id}`, is_completed: false })),
};
const ok = () => vi.fn().mockResolvedValue(DATA);
const call = (deviceId: string, extract = ok(), itemId: string = crypto.randomUUID(), text = "Kerjakan laporan", limits = LIMITS) =>
  handleExtract({ ocr_text: text, device_id: deviceId, item_id: itemId }, { db: env.DB, extract, salt: SALT, limits, now: NOW });
const setQuota = (deviceId: string, sql: string, ...args: unknown[]) =>
  env.DB.prepare(`INSERT INTO quota (device_key, month, rewards_day) VALUES (?1, '2026-09', '2026-09-29')
                  ON CONFLICT(device_key) DO NOTHING`).bind(deviceKey(deviceId, SALT)).run()
    .then(() => env.DB.prepare(sql).bind(deviceKey(deviceId, SALT), ...args).run());
const usedOf = async (deviceId: string) =>
  (await env.DB.prepare("SELECT used FROM quota WHERE device_key = ?").bind(deviceKey(deviceId, SALT)).first<{ used: number }>())?.used;

describe("handleExtract", () => {
  it("charges one use and trims tasks for the free tier", async () => {
    const r = await call(newDeviceId());
    expect(r.quota).toEqual({ used: 1, limit: 15 });
    expect(r.tasks_total).toBe(3);
    expect(r.data.tasks).toHaveLength(1);
  });

  it("does not charge twice for the same item", async () => {
    const dev = newDeviceId();
    const item = crypto.randomUUID();
    await call(dev, ok(), item);
    expect((await call(dev, ok(), item)).quota.used).toBe(1);
  });

  it("lets an already charged item through at the limit", async () => {
    const dev = newDeviceId();
    const item = crypto.randomUUID();
    await call(dev, ok(), item);
    await setQuota(dev, "UPDATE quota SET used = 15 WHERE device_key = ?1");
    await expect(call(dev, ok(), item)).resolves.toMatchObject({ quota: { used: 15 } });
  });

  it("rejects with resource-exhausted without calling the LLM", async () => {
    const dev = newDeviceId();
    await setQuota(dev, "UPDATE quota SET used = 15 WHERE device_key = ?1");
    const extract = ok();
    await expect(call(dev, extract)).rejects.toMatchObject({ code: "resource-exhausted" });
    expect(extract).not.toHaveBeenCalled();
  });

  it("does not charge when the LLM fails", async () => {
    const dev = newDeviceId();
    await expect(call(dev, vi.fn().mockRejectedValue(new LlmUnavailable("x")))).rejects.toMatchObject({ code: "unavailable" });
    expect(await usedOf(dev)).toBeUndefined();
  });

  it("gives premium users every task and the premium limit", async () => {
    const dev = newDeviceId();
    await setQuota(dev, "UPDATE quota SET premium_until = ?2 WHERE device_key = ?1", NOW + DAY);
    const r = await call(dev);
    expect(r.data.tasks).toHaveLength(3);
    expect(r.quota.limit).toBe(300);
  });

  it("resets usage in a new month", async () => {
    const dev = newDeviceId();
    await setQuota(dev, "UPDATE quota SET month = '2026-08', used = 15 WHERE device_key = ?1");
    expect((await call(dev)).quota.used).toBe(1);
  });

  it("applies the limits it is given", async () => {
    const dev = newDeviceId();
    await call(dev, ok(), crypto.randomUUID(), "Kerjakan laporan", { ...LIMITS, limitFree: 1 });
    await expect(call(dev, ok(), crypto.randomUUID(), "Kerjakan laporan", { ...LIMITS, limitFree: 1 })).rejects.toMatchObject({
      code: "resource-exhausted",
    });
  });

  it("rejects bad input", async () => {
    const dev = newDeviceId();
    await expect(call(dev, ok(), crypto.randomUUID(), "   ")).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call(dev, ok(), crypto.randomUUID(), "x".repeat(20_001))).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call(dev, ok(), "not-a-uuid")).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call("abc")).rejects.toMatchObject({ code: "invalid-argument" });
  });
});
