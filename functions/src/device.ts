import { createHash } from "node:crypto";
import { ApiError } from "./errors";

export const sha256 = (s: string): string => createHash("sha256").update(s).digest("hex");

const DEVICE_ID = /^[0-9a-f]{64}$/;

/** device_id is SHA-256(ANDROID_ID) computed on the phone; the server salt keeps it out of Firestore keys. */
export function deviceKey(deviceId: unknown, salt: string): string {
  if (typeof deviceId !== "string" || !DEVICE_ID.test(deviceId)) throw new ApiError("invalid-argument", "device_id");
  return sha256(deviceId + salt);
}
