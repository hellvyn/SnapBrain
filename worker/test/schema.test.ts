import { describe, expect, it } from "vitest";
import { LlmOutput, toExtractData, trimForTier } from "../src/schema";

const out = (o: Record<string, unknown> = {}) =>
  LlmOutput.parse({
    category: "shopping",
    title: "Paket Shopee sedang dikirim",
    extracted_info: [{ key: "No. Resi", value: "JP1234567890" }],
    action_type: "track_parcel",
    action_payload: "JP1234567890",
    tasks: [],
    ...o,
  });

describe("toExtractData", () => {
  it("maps key/value pairs to an object and numbers tasks", () => {
    const d = toExtractData(out({ tasks: ["Beli kertas", "Print laporan"] }));
    expect(d.extracted_info).toEqual({ "No. Resi": "JP1234567890" });
    expect(d.tasks).toEqual([
      { id: 1, description: "Beli kertas", is_completed: false },
      { id: 2, description: "Print laporan", is_completed: false },
    ]);
  });

  it("cuts the title to five words", () => {
    expect(toExtractData(out({ title: "satu dua tiga empat lima enam tujuh" })).title).toBe("satu dua tiga empat lima");
  });

  it("downgrades non-http open_url to none", () => {
    for (const payload of ["javascript:alert(1)", "intent://scan#Intent;end", "file:///sdcard/x"]) {
      const d = toExtractData(out({ action_type: "open_url", action_payload: payload }));
      expect(d).toMatchObject({ action_type: "none", action_payload: "" });
    }
    expect(toExtractData(out({ action_type: "open_url", action_payload: "https://toko.id/p/1" }))).toMatchObject({
      action_type: "open_url",
      action_payload: "https://toko.id/p/1",
    });
  });

  it("clears the payload when there is no action", () => {
    expect(toExtractData(out({ action_type: "none", action_payload: "sisa" })).action_payload).toBe("");
  });

  it("rejects unknown categories", () => {
    expect(() => out({ category: "gossip" })).toThrow();
  });
});

describe("trimForTier", () => {
  const d = toExtractData(out({ tasks: ["a", "b", "c"] }));

  it("keeps one task for free users and reports the total", () => {
    const r = trimForTier(d, false);
    expect(r.data.tasks).toHaveLength(1);
    expect(r.tasks_total).toBe(3);
  });

  it("keeps all tasks for premium users", () => {
    expect(trimForTier(d, true).data.tasks).toHaveLength(3);
  });
});
