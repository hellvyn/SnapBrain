import { type AuthKeys, googleKeys, verifyRequest } from "./auth";
import { type Env, limitsOf } from "./config";
import { runDaily } from "./daily";
import { ApiError, httpStatusOf, wireCodeOf } from "./errors";
import { handleExtract } from "./extract";
import { createExtractor, type ExtractFn } from "./llm";
import { googlePlayApi } from "./play";
import { handleVerifyPurchase, type PlayApi } from "./purchases";
import { fetchAdmobKeys, handleReward, type KeyFetcher } from "./reward";

interface Overrides {
  keys?: AuthKeys;
  extractor?: (env: Env) => ExtractFn;
  getKeys?: KeyFetcher;
  play?: (env: Env) => PlayApi | null;
  now?: () => number;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const playOf = (env: Env): PlayApi | null =>
  env.PLAY_SERVICE_ACCOUNT_JSON ? googlePlayApi(env.PLAY_SERVICE_ACCOUNT_JSON, env.PACKAGE_NAME) : null;

export function createApp(o: Overrides = {}) {
  const now = o.now ?? Date.now;
  const extractorOf = o.extractor ?? ((env: Env) =>
    createExtractor({ baseUrl: env.LLM_BASE_URL, model: env.LLM_MODEL, apiKey: env.LLM_API_KEY ?? "" }));

  async function route(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const project = { id: env.FIREBASE_PROJECT_ID, number: env.FIREBASE_PROJECT_NUMBER };

    if (req.method === "GET" && url.pathname === "/ad-reward") {
      if (!env.DEVICE_SALT) throw new ApiError("unavailable", "DEVICE_SALT not set");
      const q = req.url.indexOf("?");
      const result = await handleReward(q < 0 ? "" : req.url.slice(q + 1), {
        db: env.DB,
        salt: env.DEVICE_SALT,
        limits: limitsOf(env),
        getKeys: o.getKeys ?? fetchAdmobKeys,
        now: now(),
        adUnitId: env.ADMOB_AD_UNIT_ID,
      });
      console.log(JSON.stringify({ route: "ad-reward", result }));
      return new Response("ok");
    }

    if (req.method === "POST" && (url.pathname === "/extract" || url.pathname === "/verify-purchase")) {
      await verifyRequest(req.headers, project, o.keys ?? googleKeys());
      if (!env.DEVICE_SALT) throw new ApiError("unavailable", "DEVICE_SALT not set");
      const body = (await req.json().catch(() => ({}))) as unknown;
      if (typeof body !== "object" || body === null || Array.isArray(body)) throw new ApiError("invalid-argument", "body");
      if (url.pathname === "/extract") {
        if (!env.LLM_API_KEY) throw new ApiError("unavailable", "LLM_API_KEY not set");
        return json(await handleExtract(body as Record<string, unknown>, { db: env.DB, extract: extractorOf(env), salt: env.DEVICE_SALT, limits: limitsOf(env), now: now() }));
      }
      return json(await handleVerifyPurchase(body as Record<string, unknown>, { db: env.DB, play: (o.play ?? playOf)(env), salt: env.DEVICE_SALT, now: now() }));
    }
    return json({ error: "NOT_FOUND" }, 404);
  }

  return {
    async fetch(req: Request, env: Env): Promise<Response> {
      try {
        return await route(req, env);
      } catch (e) {
        if (e instanceof ApiError) return json({ error: wireCodeOf(e.code) }, httpStatusOf(e.code));
        // Never log request bodies or error messages: both can carry OCR text.
        console.error(JSON.stringify({ error: e instanceof Error ? e.name : "unknown" }));
        return json({ error: "INTERNAL" }, 500);
      }
    },
  };
}

const app = createApp();

export default {
  fetch: (req: Request, env: Env) => app.fetch(req, env),
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(runDaily({ db: env.DB, play: playOf(env), now: Date.now() }).then((r) => console.log(JSON.stringify(r))).catch((e: unknown) => console.error(JSON.stringify({ cron: "daily", error: e instanceof Error ? e.name : "unknown" }))));
  },
} satisfies ExportedHandler<Env>;
