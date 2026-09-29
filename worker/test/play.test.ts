import { exportPKCS8, generateKeyPair } from "jose";
import { expect, it, vi } from "vitest";
import { googlePlayApi } from "../src/play";

it("gets a token with a signed JWT and reads subscriptionsv2", async () => {
  const { privateKey } = await generateKeyPair("RS256", { extractable: true });
  const sa = JSON.stringify({ client_email: "sa@x.iam.gserviceaccount.com", private_key: await exportPKCS8(privateKey) });
  const f = vi.fn<typeof fetch>()
    .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "at", expires_in: 3600 })))
    .mockResolvedValueOnce(new Response(JSON.stringify({
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
      lineItems: [{ productId: "premium_monthly", expiryTime: "2026-10-29T00:00:00Z" }],
    })));
  const sub = await googlePlayApi(sa, "com.snapbrain.app", f).getSubscription("tok/1");
  expect(sub).toEqual({ state: "SUBSCRIPTION_STATE_ACTIVE", expiryMs: Date.parse("2026-10-29T00:00:00Z"), acknowledged: false, productId: "premium_monthly" });
  expect(f.mock.calls[0][0]).toBe("https://oauth2.googleapis.com/token");
  expect(f.mock.calls[1][0]).toBe(
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.snapbrain.app/purchases/subscriptionsv2/tokens/tok%2F1",
  );
  expect((f.mock.calls[1][1]?.headers as Record<string, string>).authorization).toBe("Bearer at");
});

it("defers a bad service-account secret to the first call", async () => {
  const f = vi.fn<typeof fetch>();
  const api = googlePlayApi("not json", "com.snapbrain.app", f);
  await expect(api.getSubscription("tok")).rejects.toThrow();
  expect(f).not.toHaveBeenCalled();
});
