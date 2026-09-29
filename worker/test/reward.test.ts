import { env } from "cloudflare:test";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { deviceKey } from "../src/device";
import { derToP1363, fetchAdmobKeys, handleReward, verifySsv } from "../src/reward";
import { newDeviceId, NOW, SALT } from "./helpers";

const LIMITS = { limitFree: 15, limitPremium: 300, rewardAmount: 3, rewardMaxPerDay: 3 };
let pem: string;
let privateKey: CryptoKey;

/** WebCrypto signs in r||s form; AdMob sends DER, so tests convert to DER like AdMob does. */
function p1363ToDer(sig: Uint8Array): Uint8Array {
  const int = (v: Uint8Array) => {
    let i = 0;
    while (i < v.length - 1 && v[i] === 0) i++;
    let b = v.slice(i);
    if (b[0] & 0x80) b = Uint8Array.from([0, ...b]);
    return Uint8Array.from([0x02, b.length, ...b]);
  };
  const r = int(sig.slice(0, 32));
  const s = int(sig.slice(32));
  return Uint8Array.from([0x30, r.length + s.length, ...r, ...s]);
}
const b64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

beforeAll(async () => {
  const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
  privateKey = pair.privateKey;
  const spki = new Uint8Array((await crypto.subtle.exportKey("spki", pair.publicKey)) as ArrayBuffer);
  pem = `-----BEGIN PUBLIC KEY-----\n${btoa(String.fromCharCode(...spki))}\n-----END PUBLIC KEY-----`;
});

const keys = async () => new Map([["1", pem]]);
async function signed(body: string, keyId = "1") {
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privateKey, new TextEncoder().encode(body)));
  return `${body}&signature=${b64url(p1363ToDer(raw))}&key_id=${keyId}`;
}
const query = (deviceId: string, tx: string, adUnit = "1") =>
  signed(`ad_network=1&ad_unit=${adUnit}&custom_data=${deviceId}&reward_amount=3&reward_item=ai&timestamp=1&transaction_id=${tx}&user_id=u`);
const reward = async (q: string) => handleReward(q, { db: env.DB, salt: SALT, limits: LIMITS, getKeys: keys, now: NOW, adUnitId: "1" });
const bonusOf = async (deviceId: string) =>
  (await env.DB.prepare("SELECT bonus FROM quota WHERE device_key = ?").bind(deviceKey(deviceId, SALT)).first<{ bonus: number }>())?.bonus;

describe("verifySsv", () => {
  it("accepts a valid signature and rejects tampering, unknown keys and garbage", async () => {
    const q = await signed("ad_unit=1&transaction_id=tx1");
    expect((await verifySsv(q, keys)).get("transaction_id")).toBe("tx1");
    await expect(verifySsv(q.replace("tx1", "tx2"), keys)).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(verifySsv(await signed("ad_unit=1", "9"), keys)).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(verifySsv("ad_unit=1&signature=zzz&key_id=1", keys)).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("converts DER to r||s", () => {
    const raw = new Uint8Array(64).map((_, i) => i + 1);
    expect(derToP1363(p1363ToDer(raw))).toEqual(raw);
  });
});

describe("handleReward", () => {
  it("grants the bonus", async () => {
    const dev = newDeviceId();
    expect(await reward(await query(dev, crypto.randomUUID()))).toBe("granted");
    expect(await bonusOf(dev)).toBe(3);
  });

  it("ignores a duplicate transaction", async () => {
    const dev = newDeviceId();
    const tx = crypto.randomUUID();
    await reward(await query(dev, tx));
    expect(await reward(await query(dev, tx))).toBe("duplicate");
    expect(await bonusOf(dev)).toBe(3);
  });

  it("stops at three rewards per day", async () => {
    const dev = newDeviceId();
    for (let i = 0; i < 3; i++) expect(await reward(await query(dev, crypto.randomUUID()))).toBe("granted");
    expect(await reward(await query(dev, crypto.randomUUID()))).toBe("limit");
    expect(await bonusOf(dev)).toBe(9);
  });

  it("ignores custom_data appended after the signature", async () => {
    const victim = newDeviceId();
    const q = await signed("ad_unit=1&reward_amount=3&transaction_id=" + crypto.randomUUID());
    await expect(reward(`${q}&custom_data=${victim}`)).rejects.toMatchObject({ code: "invalid-argument" });
    expect(await bonusOf(victim)).toBeUndefined();
  });

  it("rejects a callback from another ad unit and an empty configured unit", async () => {
    const dev = newDeviceId();
    await expect(reward(await query(dev, crypto.randomUUID(), "999"))).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(
      handleReward(await query(dev, crypto.randomUUID()), { db: env.DB, salt: SALT, limits: LIMITS, getKeys: keys, now: NOW, adUnitId: "" }),
    ).rejects.toMatchObject({ code: "invalid-argument" });
    expect(await bonusOf(dev)).toBeUndefined();
  });
});

describe("fetchAdmobKeys", () => {
  afterEach(() => vi.restoreAllMocks());

  it("refetches on an unknown key only when the cache is over 5 minutes old", async () => {
    const respond = (ids: number[]) => async () => Response.json({ keys: ids.map((keyId) => ({ keyId, pem })) });
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(respond([1]));
    const t0 = Date.now();
    await fetchAdmobKeys();
    fetchSpy.mockImplementation(respond([1, 2]));
    expect((await fetchAdmobKeys(true)).has("2")).toBe(false); // cache is fresh
    vi.spyOn(Date, "now").mockReturnValue(t0 + 6 * 60 * 1000);
    expect((await fetchAdmobKeys(true)).has("2")).toBe(true);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });
});
