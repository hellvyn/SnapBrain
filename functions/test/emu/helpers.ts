import { randomBytes } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Run emulator tests via `npm test`");
if (getApps().length === 0) initializeApp({ projectId: "demo-snapbrain" });

export const db = getFirestore();
export const SALT = "test-salt";
export const NOW = Date.UTC(2026, 8, 29, 5, 0); // 29 Sep 2026 12:00 WIB
export const DAY = 24 * 60 * 60 * 1000;
export const newDeviceId = (): string => randomBytes(32).toString("hex");
