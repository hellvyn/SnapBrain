import { describe, expect, it } from "vitest";
import { deviceKey, sha256 } from "../src/device";

const ID = "a".repeat(64);

describe("device", () => {
  it("hashes with sha256 hex", () => {
    expect(sha256("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });

  it("derives a stable salted key", () => {
    expect(deviceKey(ID, "s1")).toBe(deviceKey(ID, "s1"));
    expect(deviceKey(ID, "s1")).not.toBe(deviceKey(ID, "s2"));
    expect(deviceKey(ID, "s1")).toBe(sha256(ID + "s1"));
  });

  it("rejects anything that is not 64 lowercase hex chars", () => {
    for (const bad of ["abc", "A".repeat(64), "g".repeat(64), 42, undefined, null]) {
      expect(() => deviceKey(bad, "s")).toThrow(expect.objectContaining({ code: "invalid-argument" }));
    }
  });
});
