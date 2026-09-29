import { google } from "googleapis";
import type { PlayApi } from "./purchases";

/** Thin wiring to the Android Publisher API; all decisions live in purchases.ts. */
export function googlePlayApi(packageName: string): PlayApi {
  const publisher = google.androidpublisher({
    version: "v3",
    auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
  });
  return {
    async getSubscription(token) {
      const { data } = await publisher.purchases.subscriptionsv2.get({ packageName, token });
      const item = data.lineItems?.[0];
      return {
        state: data.subscriptionState ?? "",
        expiryMs: item?.expiryTime ? Date.parse(item.expiryTime) : null,
        acknowledged: data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        productId: item?.productId ?? "",
      };
    },
    async acknowledge(productId, token) {
      await publisher.purchases.subscriptions.acknowledge({ packageName, subscriptionId: productId, token, requestBody: {} });
    },
  };
}
