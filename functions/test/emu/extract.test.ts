import { randomUUID } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetConfigCache } from "../../src/config";
import { deviceKey } from "../../src/device";
import { handleExtract } from "../../src/extract";
import { LlmUnavailable } from "../../src/llm";
import type { ExtractData } from "../../src/schema";
import { DAY, db, newDeviceId, NOW, SALT } from "./helpers";

const DATA: ExtractData = {
  category: "task",
  title: "Tugas kelompok",
  extracted_info: {},
  action_type: "none",
  action_payload: "",
  tasks: [1, 2, 3].map((id) => ({ id, description: `Langkah ${id}`, is_completed: false })),
};
const ok = () => vi.fn().mockResolvedValue(DATA);
const call = (deviceId: string, extract = ok(), itemId = randomUUID(), text = "Kerjakan laporan") =>
  handleExtract({ ocr_text: text, device_id: deviceId, item_id: itemId }, { db, extract, salt: SALT, now: NOW });
const quotaRef = (deviceId: string) => db.doc(`quota/${deviceKey(deviceId, SALT)}`);

beforeEach(() => resetConfigCache());

describe("handleExtract", () => {
  it("charges one use and trims tasks for the free tier", async () => {
    const r = await call(newDeviceId());
    expect(r.quota).toEqual({ used: 1, limit: 15 });
    expect(r.tasks_total).toBe(3);
    expect(r.data.tasks).toHaveLength(1);
  });

  it("does not charge twice for the same item", async () => {
    const dev = newDeviceId();
    const item = randomUUID();
    await call(dev, ok(), item);
    expect((await call(dev, ok(), item)).quota.used).toBe(1);
  });

  it("lets an already charged item through at the limit", async () => {
    const dev = newDeviceId();
    const item = randomUUID();
    await call(dev, ok(), item);
    await quotaRef(dev).set({ used: 15 }, { merge: true });
    await expect(call(dev, ok(), item)).resolves.toMatchObject({ quota: { used: 15 } });
  });

  it("rejects with resource-exhausted without calling the LLM when quota is used up", async () => {
    const dev = newDeviceId();
    await quotaRef(dev).set({ month: "2026-09", used: 15 });
    const extract = ok();
    await expect(call(dev, extract)).rejects.toMatchObject({ code: "resource-exhausted" });
    expect(extract).not.toHaveBeenCalled();
  });

  it("does not charge when the LLM fails", async () => {
    const dev = newDeviceId();
    const failing = vi.fn().mockRejectedValue(new LlmUnavailable("down"));
    await expect(call(dev, failing)).rejects.toMatchObject({ code: "unavailable" });
    expect((await quotaRef(dev).get()).exists).toBe(false);
  });

  it("gives premium users every task and the premium limit", async () => {
    const dev = newDeviceId();
    await quotaRef(dev).set({ premiumUntil: NOW + DAY });
    const r = await call(dev);
    expect(r.data.tasks).toHaveLength(3);
    expect(r.quota.limit).toBe(300);
  });

  it("resets usage in a new month", async () => {
    const dev = newDeviceId();
    await quotaRef(dev).set({ month: "2026-08", used: 15 });
    expect((await call(dev)).quota.used).toBe(1);
  });

  it("rejects bad input", async () => {
    const dev = newDeviceId();
    await expect(call(dev, ok(), randomUUID(), "   ")).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call(dev, ok(), randomUUID(), "x".repeat(20_001))).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call(dev, ok(), "not-a-uuid")).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(call("abc")).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("reads limits from config/app", async () => {
    await db.doc("config/app").set({ limitFree: 1 });
    resetConfigCache();
    const dev = newDeviceId();
    await call(dev);
    await expect(call(dev)).rejects.toMatchObject({ code: "resource-exhausted" });
    await db.doc("config/app").delete();
  });
});
