import { env } from "cloudflare:test";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/index";
import type { ExtractData } from "../src/schema";
import { newDeviceId } from "./helpers";

const DATA: ExtractData = { category: "task", title: "x", extracted_info: {}, action_type: "none", action_payload: "", tasks: [] };
let headers: Record<string, string>;
let app: ReturnType<typeof createApp>;

beforeAll(async () => {
  const { privateKey, publicKey } = await generateKeyPair("RS256");
  const set = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
  const sign = (iss: string, aud: string | string[]) =>
    new SignJWT({}).setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(iss).setAudience(aud).setSubject("u").setIssuedAt().setExpirationTime("1h").sign(privateKey);
  headers = {
    "content-type": "application/json",
    authorization: `Bearer ${await sign(`https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`, env.FIREBASE_PROJECT_ID)}`,
    "x-firebase-appcheck": await sign(`https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`, [`projects/${env.FIREBASE_PROJECT_NUMBER}`]),
  };
  app = createApp({ keys: { idToken: set, appCheck: set }, extractor: () => vi.fn().mockResolvedValue(DATA) });
});

const extract = (e: typeof env, body: unknown, h = headers) =>
  app.fetch(new Request("https://w/extract", { method: "POST", headers: h, body: JSON.stringify(body) }), e);
const withSecrets = { ...env, LLM_API_KEY: "k", DEVICE_SALT: "s" };

describe("router", () => {
  it("serves /extract with the v1 response shape", async () => {
    const res = await extract(withSecrets, { ocr_text: "Kerjakan laporan", device_id: newDeviceId(), item_id: crypto.randomUUID() });
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ data: { category: "task" }, tasks_total: 0, quota: { used: 1, limit: 15 } });
  });

  it("answers 503 UNAVAILABLE when secrets are missing", async () => {
    const res = await extract({ ...env, LLM_API_KEY: "", DEVICE_SALT: "" }, { ocr_text: "x", device_id: newDeviceId(), item_id: crypto.randomUUID() });
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({ error: "UNAVAILABLE" });
  });

  it("maps auth failures to 401 and App Check failures to 403", async () => {
    const noAuth = await extract(withSecrets, {}, { "content-type": "application/json" });
    expect(noAuth.status).toBe(401);
    expect(await noAuth.json()).toEqual({ error: "UNAUTHENTICATED" });
    const { "x-firebase-appcheck": _, ...noAppCheck } = headers;
    expect((await extract(withSecrets, {}, noAppCheck)).status).toBe(403);
  });

  it("maps invalid input to 400 and unknown routes to 404", async () => {
    const bad = await extract(withSecrets, { ocr_text: "", device_id: "x", item_id: "y" });
    expect(bad.status).toBe(400);
    expect((await app.fetch(new Request("https://w/nope"), withSecrets)).status).toBe(404);
  });
});
