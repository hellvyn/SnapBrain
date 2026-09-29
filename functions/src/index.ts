import Anthropic from "@anthropic-ai/sdk";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { defineSecret, defineString } from "firebase-functions/params";
import { type CallableRequest, HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { ApiError } from "./errors";
import { handleExtract } from "./extract";
import { createExtractor } from "./llm";
import { googlePlayApi } from "./play";
import { handleRtdn, handleVerifyPurchase } from "./purchases";
import { fetchAdmobKeys, handleReward } from "./reward";

initializeApp();
const db = getFirestore();

const REGION = "asia-southeast2";
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
const DEVICE_SALT = defineSecret("DEVICE_SALT");
const PACKAGE_NAME = defineString("PACKAGE_NAME", { default: "com.snapbrain.app" });
const ADMOB_AD_UNIT_ID = defineString("ADMOB_AD_UNIT_ID"); // no default: deploy must ask (fail-closed)

let anthropic: Anthropic | undefined;
const llmClient = () => (anthropic ??= new Anthropic({ apiKey: ANTHROPIC_API_KEY.value(), timeout: 20_000, maxRetries: 0 }));

const errorText = (e: unknown) => (e instanceof Error ? e.message : String(e));

function authedData(req: CallableRequest): Record<string, unknown> {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign-in required");
  return (req.data ?? {}) as Record<string, unknown>;
}

async function toHttpsErrors<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    if (e instanceof ApiError) throw new HttpsError(e.code, e.message);
    logger.error("unhandled", { error: errorText(e) }); // never log request data: it holds OCR text
    throw new HttpsError("internal", "internal");
  }
}

export const extract = onCall(
  { region: REGION, enforceAppCheck: true, secrets: [ANTHROPIC_API_KEY, DEVICE_SALT], timeoutSeconds: 60 },
  (req) =>
    toHttpsErrors(() =>
      handleExtract(authedData(req), {
        db,
        extract: createExtractor(llmClient()),
        salt: DEVICE_SALT.value(),
        now: Date.now(),
      }),
    ),
);

export const verifyPurchase = onCall({ region: REGION, enforceAppCheck: true, secrets: [DEVICE_SALT] }, (req) =>
  toHttpsErrors(() =>
    handleVerifyPurchase(authedData(req), {
      db,
      play: googlePlayApi(PACKAGE_NAME.value()),
      salt: DEVICE_SALT.value(),
      now: Date.now(),
    }),
  ),
);

export const adReward = onRequest({ region: REGION, secrets: [DEVICE_SALT] }, async (req, res) => {
  const q = req.originalUrl.indexOf("?");
  try {
    const result = await handleReward(q < 0 ? "" : req.originalUrl.slice(q + 1), {
      db,
      salt: DEVICE_SALT.value(),
      getKeys: fetchAdmobKeys,
      now: Date.now(),
      adUnitId: ADMOB_AD_UNIT_ID.value(),
    });
    logger.info("reward", { result });
    res.status(200).send("ok");
  } catch (e) {
    const rejected = e instanceof ApiError;
    logger.warn("reward failed", { error: errorText(e) });
    res.status(rejected ? 400 : 500).send(rejected ? "bad request" : "error");
  }
});

export const playRtdn = onMessagePublished({ topic: "play-rtdn", region: REGION, retry: true }, async (event) => {
  const result = await handleRtdn(event.data.message.json, { db, play: googlePlayApi(PACKAGE_NAME.value()) });
  logger.info("rtdn", { result });
});
