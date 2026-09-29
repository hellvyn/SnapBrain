import { randomUUID } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { deviceKey } from "../../src/device";
import { handleRtdn, handleVerifyPurchase, type SubscriptionInfo } from "../../src/purchases";
import { DAY, db, newDeviceId, NOW, SALT } from "./helpers";

function fakePlay(sub: Partial<SubscriptionInfo> = {}) {
  const info: SubscriptionInfo = {
    state: "SUBSCRIPTION_STATE_ACTIVE",
    expiryMs: NOW + 30 * DAY,
    acknowledged: false,
    productId: "premium_monthly",
    ...sub,
  };
  return { getSubscription: vi.fn().mockResolvedValue(info), acknowledge: vi.fn().mockResolvedValue(undefined) };
}
const verifyP = (deviceId: string, token: string, play = fakePlay()) =>
  handleVerifyPurchase({ purchase_token: token, device_id: deviceId }, { db, play, salt: SALT, now: NOW });
const premiumOf = async (deviceId: string) =>
  (await db.doc(`quota/${deviceKey(deviceId, SALT)}`).get()).get("premiumUntil");

describe("handleVerifyPurchase", () => {
  it("sets premiumUntil and acknowledges a new purchase", async () => {
    const dev = newDeviceId();
    const token = randomUUID();
    const play = fakePlay();
    expect(await verifyP(dev, token, play)).toEqual({ premium_until: NOW + 30 * DAY });
    expect(await premiumOf(dev)).toBe(NOW + 30 * DAY);
    expect(play.acknowledge).toHaveBeenCalledWith("premium_monthly", token);
  });

  it("does not acknowledge an already acknowledged purchase", async () => {
    const play = fakePlay({ acknowledged: true });
    await verifyP(newDeviceId(), randomUUID(), play);
    expect(play.acknowledge).not.toHaveBeenCalled();
  });

  it("moves premium to the latest device", async () => {
    const a = newDeviceId();
    const b = newDeviceId();
    const token = randomUUID();
    await verifyP(a, token);
    await verifyP(b, token);
    expect(await premiumOf(a)).toBeNull();
    expect(await premiumOf(b)).toBe(NOW + 30 * DAY);
  });

  it("grants nothing for an expired subscription", async () => {
    const dev = newDeviceId();
    const play = fakePlay({ state: "SUBSCRIPTION_STATE_EXPIRED" });
    expect(await verifyP(dev, randomUUID(), play)).toEqual({ premium_until: null });
    expect(await premiumOf(dev)).toBeNull();
    expect(play.acknowledge).not.toHaveBeenCalled();
  });

  it("rejects a missing token and maps Play failures to unavailable", async () => {
    await expect(verifyP(newDeviceId(), "")).rejects.toMatchObject({ code: "invalid-argument" });
    const broken = { getSubscription: vi.fn().mockRejectedValue(new Error("410")), acknowledge: vi.fn() };
    await expect(verifyP(newDeviceId(), randomUUID(), broken)).rejects.toMatchObject({ code: "unavailable" });
  });
});

describe("handleRtdn", () => {
  it("updates the linked device from Play", async () => {
    const dev = newDeviceId();
    const token = randomUUID();
    await verifyP(dev, token);
    const revoked = fakePlay({ state: "SUBSCRIPTION_STATE_EXPIRED" });
    const msg = { subscriptionNotification: { purchaseToken: token, notificationType: 12 } };
    expect(await handleRtdn(msg, { db, play: revoked })).toBe("updated");
    expect(await premiumOf(dev)).toBeNull();
  });

  it("ignores unknown tokens and non-subscription messages", async () => {
    const play = fakePlay();
    expect(await handleRtdn({ subscriptionNotification: { purchaseToken: randomUUID() } }, { db, play })).toBe("unknown");
    expect(await handleRtdn({ testNotification: {} }, { db, play })).toBe("ignored");
  });
});
