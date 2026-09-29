import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";
import { ApiError } from "./errors";

export interface AuthKeys {
  idToken: JWTVerifyGetKey;
  appCheck: JWTVerifyGetKey;
}

const ID_TOKEN_JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APP_CHECK_JWKS = "https://firebaseappcheck.googleapis.com/v1/jwks";

let cached: AuthKeys | undefined;
/** Remote key sets cache keys between requests in the same isolate. */
export const googleKeys = (): AuthKeys =>
  (cached ??= { idToken: createRemoteJWKSet(new URL(ID_TOKEN_JWKS)), appCheck: createRemoteJWKSet(new URL(APP_CHECK_JWKS)) });

export async function verifyRequest(
  headers: Headers,
  project: { id: string; number: string },
  keys: AuthKeys,
  appCheckRequired = true,
): Promise<{ uid: string; appCheck: boolean }> {
  const bearer = headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!bearer) throw new ApiError("unauthenticated", "missing id token");
  let uid: string;
  try {
    const { payload } = await jwtVerify(bearer, keys.idToken, {
      issuer: `https://securetoken.google.com/${project.id}`,
      audience: project.id,
      algorithms: ["RS256"],
    });
    if (!payload.sub) throw new Error("no sub");
    uid = payload.sub;
  } catch {
    throw new ApiError("unauthenticated", "invalid id token");
  }

  const appCheck = headers.get("x-firebase-appcheck");
  if (!appCheck) {
    if (appCheckRequired) throw new ApiError("failed-precondition", "missing app check");
    return { uid, appCheck: false };
  }
  try {
    await jwtVerify(appCheck, keys.appCheck, {
      issuer: `https://firebaseappcheck.googleapis.com/${project.number}`,
      audience: `projects/${project.number}`,
      algorithms: ["RS256"],
    });
  } catch {
    if (appCheckRequired) throw new ApiError("failed-precondition", "invalid app check");
    return { uid, appCheck: false };
  }
  return { uid, appCheck: true };
}
