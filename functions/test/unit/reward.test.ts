import { generateKeyPairSync, sign } from "node:crypto";
import { describe, expect, it } from "vitest";
import { verifySsv } from "../../src/reward";

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const keys = async () => new Map([["123", publicKey.export({ type: "spki", format: "pem" }).toString()]]);
const signed = (body: string, keyId = "123") =>
  `${body}&signature=${sign("sha256", Buffer.from(body), privateKey).toString("base64url")}&key_id=${keyId}`;

const BODY =
  "ad_network=5450213213286189855&ad_unit=1234&custom_data=abc&reward_amount=3&reward_item=ai&timestamp=1790000000000&transaction_id=tx1&user_id=u";

describe("verifySsv", () => {
  it("accepts a valid signature and returns the params", async () => {
    expect((await verifySsv(signed(BODY), keys)).get("transaction_id")).toBe("tx1");
  });

  it("rejects a tampered query", async () => {
    const tampered = signed(BODY).replace("reward_amount=3", "reward_amount=300");
    await expect(verifySsv(tampered, keys)).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("rejects an unknown key id", async () => {
    await expect(verifySsv(signed(BODY, "999"), keys)).rejects.toMatchObject({ code: "invalid-argument" });
  });

  it("rejects a missing or garbage signature", async () => {
    await expect(verifySsv(BODY, keys)).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(verifySsv(`${BODY}&signature=zzz&key_id=123`, keys)).rejects.toMatchObject({ code: "invalid-argument" });
  });
});
