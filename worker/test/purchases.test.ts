import { env } from "cloudflare:test";
import { describe, expect, it, vi } from "vitest";
import { runDaily } from "../src/daily";
import { deviceKey } from "../src/device";
import { handleVerifyPurchase, type PlayApi, type SubscriptionInfo } from "../src/purchases";
import { DAY, newDeviceId, NOW, SALT } from "./helpers";

function fakePlay(sub: Partial<SubscriptionInfo> = {}) {
  const info: SubscriptionInfo = {
    state: "SUBSCRIPTION_STATE_ACTIVE",
    expiryMs: NOW + 30 * DAY,
    acknowledged: false,
    productId: "premium_monthly",
    ...sub,
  };
  return { getSubscription: vi.fn().mockResolvedValue(info), acknowledge: vi.fn().mockResolvedValue(undefined) } satisfies PlayApi;
}
const verifyP = (deviceId: string, token: string, play: PlayApi | null = fakePlay()) =>
  handleVerifyPurchase({ purchase_token: token, device_id: deviceId }, { db: env.DB, play, salt: SALT, now: NOW });
const premiumOf = async (deviceId: string) =>
  (await env.DB.prepare("SELECT premium_until FROM quota WHERE device_key = ?").bind(deviceKey(deviceId, SALT)).first<{ premium_until: number | null }>())
    ?.premium_until ?? null;

describe("handleVerifyPurchase", () => {
  it("sets premium and acknowledges a new purchase", async () => {
    const dev = newDeviceId();
    const token = crypto.randomUUID();
    const play = fakePlay();
    expect(await verifyP(dev, token, play)).toEqual({ premium_until: NOW + 30 * DAY });
    expect(await premiumOf(dev)).toBe(NOW + 30 * DAY);
    expect(play.acknowledge).toHaveBeenCalledWith("premium_monthly", token);
  });

  it("moves premium to the latest device", async () => {
    const a = newDeviceId();
    const b = newDeviceId();
    const token = crypto.randomUUID();
    await verifyP(a, token);
    await verifyP(b, token);
    expect(await premiumOf(a)).toBeNull();
    expect(await premiumOf(b)).toBe(NOW + 30 * DAY);
  });

  it("does not let an expired old token wipe a newer grant", async () => {
    const dev = newDeviceId();
    const t1 = crypto.randomUUID();
    await verifyP(dev, t1);
    await verifyP(dev, crypto.randomUUID(), fakePlay({ expiryMs: NOW + 60 * DAY }));
    await verifyP(dev, t1, fakePlay({ state: "SUBSCRIPTION_STATE_EXPIRED" }));
    expect(await premiumOf(dev)).toBe(NOW + 60 * DAY);
  });

  it.each([
    ["SUBSCRIPTION_STATE_CANCELED", true],
    ["SUBSCRIPTION_STATE_IN_GRACE_PERIOD", true],
    ["SUBSCRIPTION_STATE_ON_HOLD", false],
    ["SUBSCRIPTION_STATE_PENDING", false],
  ])("state %s grants premium: %s", async (state, granted) => {
    const dev = newDeviceId();
    const r = await verifyP(dev, crypto.randomUUID(), fakePlay({ state }));
    expect(r.premium_until !== null).toBe(granted);
  });

  it("is unavailable without Play credentials and rejects a missing token", async () => {
    await expect(verifyP(newDeviceId(), crypto.randomUUID(), null)).rejects.toMatchObject({ code: "unavailable" });
    await expect(verifyP(newDeviceId(), "")).rejects.toMatchObject({ code: "invalid-argument" });
  });
});

describe("runDaily", () => {
  it("revokes a refunded subscription and deletes expired idempotency rows", async () => {
    const dev = newDeviceId();
    const token = crypto.randomUUID();
    await verifyP(dev, token);
    await env.DB.prepare("INSERT INTO rewards (tx_hash, granted, expire_at) VALUES ('old', 1, ?)").bind(NOW - 1).run();
    await runDaily({ db: env.DB, play: fakePlay({ state: "SUBSCRIPTION_STATE_EXPIRED" }), now: NOW });
    expect(await premiumOf(dev)).toBeNull();
    expect(await env.DB.prepare("SELECT 1 FROM rewards WHERE tx_hash = 'old'").first()).toBeNull();
  });
});
