import { describe, expect, it } from "vitest";
import { LlmOutput, normalizeAction, toExtractData, validDue } from "../src/schema";

const out = (o: Record<string, unknown> = {}) =>
  LlmOutput.parse({ category: "reference", title: "Resep Pepes Ayam", info: [], lists: [], actions: [], activation: "masak", ...o });
const list = (items: unknown[], extra: Record<string, unknown> = {}) => ({ title: "Bumbu", kind: "checklist", role: "belanja", items, ...extra });
const item = (text: string, extra: Record<string, unknown> = {}) => ({ text, due: "", minutes: 0, price: 0, size: "", ...extra });

describe("LlmOutput", () => {
  it("accepts sloppy item fields instead of dropping the whole answer", () => {
    const o = out({ lists: [list(["1 kg ayam", { text: "Gamis", price: "189000", minutes: "x" }, { due: "2026-10-01" }])] });
    expect(o.lists[0].items).toEqual([item("1 kg ayam"), item("Gamis", { price: 189000 })]);
  });

  it("falls back for unknown kind, role and activation", () => {
    const o = out({ lists: [list([item("a")], { kind: "table", role: "hobi" })], activation: "dance" });
    expect(o.lists[0]).toMatchObject({ kind: "checklist", role: "lainnya" });
    expect(o.activation).toBe("none");
  });

  it("treats missing arrays as empty", () => {
    expect(LlmOutput.parse({ category: "task", title: "x" })).toMatchObject({ info: [], lists: [], actions: [], activation: "none" });
  });

  it("still rejects an unknown category so the adapter retries", () => {
    expect(() => out({ category: "gossip" })).toThrow();
  });
});

describe("toExtractData", () => {
  it("caps lists, items and text length", () => {
    const many = Array.from({ length: 45 }, (_, i) => item(`bahan ${i}`));
    const d = toExtractData(out({ lists: Array.from({ length: 7 }, () => list(many)).concat([]), info: [] }));
    expect(d.lists).toHaveLength(6);
    expect(d.lists[0].items).toHaveLength(40);
    const long = toExtractData(out({ lists: [list([item("x".repeat(250))])] }));
    expect(long.lists[0].items[0].text).toHaveLength(200);
  });

  it("drops blank and duplicate items and empty lists", () => {
    const d = toExtractData(out({ lists: [list([item("Bawang merah"), item("  "), item("bawang MERAH")]), list([item(" ")], { title: "Kosong" })] }));
    expect(d.lists).toHaveLength(1);
    expect(d.lists[0].items.map((i) => i.text)).toEqual(["Bawang merah"]);
  });

  it("keeps only valid dues and sane numbers", () => {
    const d = toExtractData(out({
      lists: [list([
        item("a", { due: "2026-10-01T23:59", minutes: 30.4, price: 189000 }),
        item("b", { due: "2026-02-30", minutes: 2000, price: -5 }),
        item("c", { due: "besok", size: "  500 ml  " }),
      ])],
    }));
    expect(d.lists[0].items).toEqual([
      { text: "a", due: "2026-10-01T23:59", minutes: 30, price: 189000, size: "" },
      { text: "b", due: "", minutes: 0, price: 0, size: "" },
      { text: "c", due: "", minutes: 0, price: 0, size: "500 ml" },
    ]);
  });

  it("names untitled lists", () => {
    expect(toExtractData(out({ lists: [list([item("a")], { title: " " })] })).lists[0].title).toBe("Daftar");
  });

  it("keeps the first eight info entries and ignores empty ones", () => {
    const info = [{ key: "__proto__", value: "x" }, { key: " ", value: "y" }, { key: "Porsi", value: "" }]
      .concat(Array.from({ length: 10 }, (_, i) => ({ key: `K${i}`, value: `v${i}` })));
    const d = toExtractData(out({ info }));
    expect(Object.keys(d.info)).toHaveLength(8);
    expect(Object.getPrototypeOf(d.info)).toBe(Object.prototype);
    expect(d.info.__proto__).toBe("x");
  });

  it("cuts the title to five words", () => {
    expect(toExtractData(out({ title: "satu dua tiga empat lima enam" })).title).toBe("satu dua tiga empat lima");
  });

  it("keeps at most three valid, distinct actions", () => {
    const d = toExtractData(out({
      actions: [
        { type: "open_url", payload: "javascript:alert(1)" },
        { type: "copy_text", payload: "123" },
        { type: "copy_text", payload: "123" },
        { type: "open_maps", payload: "Sinarmas Land" },
        { type: "whatsapp", payload: "0812-3456-789" },
        { type: "call", payload: "021 555 1234" },
      ],
    }));
    expect(d.actions).toEqual([
      { type: "copy_text", payload: "123" },
      { type: "open_maps", payload: "Sinarmas Land" },
      { type: "whatsapp", payload: "628123456789" },
    ]);
  });
});

describe("normalizeAction", () => {
  it("validates calendar payloads", () => {
    expect(normalizeAction("add_calendar", "2026-10-02T10:00| Workshop Lumina ")).toEqual({ type: "add_calendar", payload: "2026-10-02T10:00|Workshop Lumina" });
    expect(normalizeAction("add_calendar", "2026-10-02|Workshop")).toBeNull();
    expect(normalizeAction("add_calendar", "2026-13-02T10:00|Workshop")).toBeNull();
    expect(normalizeAction("add_calendar", "2026-10-02T10:00|")).toBeNull();
  });

  it("opens only http(s) urls", () => {
    expect(normalizeAction("open_url", "https://cookpad.com/id/resep/1")).not.toBeNull();
    for (const bad of ["javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/x", "https://a b"]) expect(normalizeAction("open_url", bad)).toBeNull();
  });

  it("normalizes phone numbers", () => {
    expect(normalizeAction("whatsapp", "+62 815-1920-1166")).toEqual({ type: "whatsapp", payload: "6281519201166" });
    expect(normalizeAction("whatsapp", "0815 1920 1166")).toEqual({ type: "whatsapp", payload: "6281519201166" });
    expect(normalizeAction("whatsapp", "81519201166")).toEqual({ type: "whatsapp", payload: "6281519201166" });
    expect(normalizeAction("whatsapp", "hubungi admin")).toBeNull();
    expect(normalizeAction("call", "+62 21 555 1234")).toEqual({ type: "call", payload: "+62215551234" });
    expect(normalizeAction("call", "12")).toBeNull();
  });

  it("checks resi, copy text and maps length", () => {
    expect(normalizeAction("track_parcel", "JP1234567890")).not.toBeNull();
    expect(normalizeAction("track_parcel", "JP 123; DROP")).toBeNull();
    expect(normalizeAction("copy_text", "x".repeat(201))).toBeNull();
    expect(normalizeAction("open_maps", "")).toBeNull();
  });

  it("maps unknown marketplaces to other", () => {
    expect(normalizeAction("search_product", "Shopee|Gamis Katun")).toEqual({ type: "search_product", payload: "shopee|Gamis Katun" });
    expect(normalizeAction("search_product", "tiktok|Gamis")).toEqual({ type: "search_product", payload: "other|Gamis" });
    expect(normalizeAction("search_product", "Gamis")).toEqual({ type: "search_product", payload: "other|Gamis" });
    expect(normalizeAction("search_product", "shopee| ")).toBeNull();
  });

  it("rejects unknown types", () => {
    expect(normalizeAction("teleport", "x")).toBeNull();
  });
});

describe("validDue", () => {
  it("accepts real dates with optional time", () => {
    expect(validDue("2026-10-01")).toBe("2026-10-01");
    expect(validDue(" 2026-10-01T09:30 ")).toBe("2026-10-01T09:30");
  });

  it("rejects impossible or free-form values", () => {
    for (const bad of ["2026-02-30", "2026-10-01T24:00", "2026-10-01T09:60", "besok", "2026-10-01 09:30", ""]) expect(validDue(bad)).toBeNull();
  });
});
