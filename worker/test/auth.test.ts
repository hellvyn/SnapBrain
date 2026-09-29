import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { beforeAll, describe, expect, it } from "vitest";
import { type AuthKeys, verifyRequest } from "../src/auth";

const PROJECT = { id: "snapbrain-hellvyn", number: "472964840390" };
let keys: AuthKeys;
let sign: (claims: Record<string, unknown>, opts: { iss: string; aud: string | string[]; sub?: string; exp?: string }) => Promise<string>;

beforeAll(async () => {
  const { privateKey, publicKey } = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(publicKey)), kid: "k1", alg: "RS256" };
  const set = createLocalJWKSet({ keys: [jwk] });
  keys = { idToken: set, appCheck: set };
  sign = (claims, o) =>
    new SignJWT(claims)
      .setProtectedHeader({ alg: "RS256", kid: "k1" })
      .setIssuer(o.iss)
      .setAudience(o.aud)
      .setSubject(o.sub ?? "user-1")
      .setIssuedAt()
      .setExpirationTime(o.exp ?? "1h")
      .sign(privateKey);
});

const idToken = () => sign({}, { iss: `https://securetoken.google.com/${PROJECT.id}`, aud: PROJECT.id });
const appCheck = () =>
  sign({}, {
    iss: `https://firebaseappcheck.googleapis.com/${PROJECT.number}`,
    aud: [`projects/${PROJECT.number}`, `projects/${PROJECT.id}`],
  });
const headers = (id?: string, ac?: string) => {
  const h = new Headers();
  if (id) h.set("authorization", `Bearer ${id}`);
  if (ac) h.set("x-firebase-appcheck", ac);
  return h;
};

describe("verifyRequest", () => {
  it("accepts a valid ID token and App Check token (array aud)", async () => {
    await expect(verifyRequest(headers(await idToken(), await appCheck()), PROJECT, keys)).resolves.toEqual({ uid: "user-1", appCheck: true });
  });

  it("rejects a missing or foreign ID token as unauthenticated", async () => {
    await expect(verifyRequest(headers(undefined, await appCheck()), PROJECT, keys)).rejects.toMatchObject({ code: "unauthenticated" });
    const foreign = await sign({}, { iss: "https://securetoken.google.com/other", aud: "other" });
    await expect(verifyRequest(headers(foreign, await appCheck()), PROJECT, keys)).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("rejects an expired ID token", async () => {
    const expired = await sign({}, { iss: `https://securetoken.google.com/${PROJECT.id}`, aud: PROJECT.id, exp: "-1m" });
    await expect(verifyRequest(headers(expired, await appCheck()), PROJECT, keys)).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("lets a request without a valid App Check token through, flagged, when App Check is optional", async () => {
    await expect(verifyRequest(headers(await idToken()), PROJECT, keys, false)).resolves.toEqual({ uid: "user-1", appCheck: false });
    const foreign = await sign({}, { iss: "https://firebaseappcheck.googleapis.com/999", aud: ["projects/999"] });
    await expect(verifyRequest(headers(await idToken(), foreign), PROJECT, keys, false)).resolves.toEqual({ uid: "user-1", appCheck: false });
    await expect(verifyRequest(headers(undefined), PROJECT, keys, false)).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("rejects a missing or foreign App Check token as failed-precondition", async () => {
    await expect(verifyRequest(headers(await idToken()), PROJECT, keys)).rejects.toMatchObject({ code: "failed-precondition" });
    const foreign = await sign({}, { iss: "https://firebaseappcheck.googleapis.com/999", aud: ["projects/999"] });
    await expect(verifyRequest(headers(await idToken(), foreign), PROJECT, keys)).rejects.toMatchObject({ code: "failed-precondition" });
  });
});
