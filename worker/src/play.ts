import { importPKCS8, SignJWT } from "jose";
import type { PlayApi } from "./purchases";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

/** Thin REST wiring to the Android Publisher API with a service-account JWT; decisions live in purchases.ts. */
export function googlePlayApi(serviceAccountJson: string, packageName: string, fetchFn: typeof fetch = fetch): PlayApi {
  const sa = JSON.parse(serviceAccountJson) as { client_email: string; private_key: string };
  let cached: { token: string; exp: number } | null = null;

  async function accessToken(): Promise<string> {
    if (cached && cached.exp > Date.now() + 60_000) return cached.token;
    const assertion = await new SignJWT({ scope: SCOPE })
      .setProtectedHeader({ alg: "RS256" })
      .setIssuer(sa.client_email)
      .setAudience(TOKEN_URL)
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(await importPKCS8(sa.private_key, "RS256"));
    const res = await fetchFn(TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
    });
    if (!res.ok) throw new Error(`token ${res.status}`);
    const body = (await res.json()) as { access_token: string; expires_in: number };
    cached = { token: body.access_token, exp: Date.now() + body.expires_in * 1000 };
    return cached.token;
  }

  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases`;
  return {
    async getSubscription(token) {
      const res = await fetchFn(`${base}/subscriptionsv2/tokens/${encodeURIComponent(token)}`, {
        headers: { authorization: `Bearer ${await accessToken()}` },
      });
      if (!res.ok) throw new Error(`play ${res.status}`);
      const data = (await res.json()) as {
        subscriptionState?: string;
        acknowledgementState?: string;
        lineItems?: { productId?: string; expiryTime?: string }[];
      };
      const item = data.lineItems?.[0];
      return {
        state: data.subscriptionState ?? "",
        expiryMs: item?.expiryTime ? Date.parse(item.expiryTime) : null,
        acknowledged: data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        productId: item?.productId ?? "",
      };
    },
    async acknowledge(productId, token) {
      const res = await fetchFn(`${base}/subscriptions/${productId}/tokens/${encodeURIComponent(token)}:acknowledge`, {
        method: "POST",
        headers: { authorization: `Bearer ${await accessToken()}`, "content-type": "application/json" },
        body: "{}",
      });
      if (!res.ok) throw new Error(`ack ${res.status}`);
    },
  };
}
