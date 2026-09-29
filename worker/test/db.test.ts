import { env } from "cloudflare:test";
import { expect, it } from "vitest";
import { limitsOf } from "../src/config";
import { httpStatusOf, wireCodeOf } from "../src/errors";

it("has the migrated tables", async () => {
  const { results } = await env.DB.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").all<{ name: string }>();
  expect(results.map((r) => r.name)).toEqual(expect.arrayContaining(["charges", "purchases", "quota", "rewards"]));
});

it("reads limits from vars", () => {
  expect(limitsOf(env)).toEqual({ limitFree: 15, limitPremium: 300, rewardAmount: 3, rewardMaxPerDay: 3 });
});

it("maps error codes to Firebase-style wire names and HTTP status", () => {
  expect(wireCodeOf("resource-exhausted")).toBe("RESOURCE_EXHAUSTED");
  expect(wireCodeOf("failed-precondition")).toBe("FAILED_PRECONDITION");
  expect(httpStatusOf("resource-exhausted")).toBe(429);
  expect(httpStatusOf("unauthenticated")).toBe(401);
});
