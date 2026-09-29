import { generateKeyPairSync, randomUUID, sign } from "node:crypto";
import { beforeEach, describe, expect, it } from "vitest";
import { resetConfigCache } from "../../src/config";
import { deviceKey } from "../../src/device";
import { handleReward } from "../../src/reward";
import { db, newDeviceId, NOW, SALT } from "./helpers";

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const getKeys = async () => new Map([["1", publicKey.export({ type: "spki", format: "pem" }).toString()]]);
const query = (deviceId: string, tx: string) => {
  const body = `ad_network=1&ad_unit=1&custom_data=${deviceId}&reward_amount=3&reward_item=ai&timestamp=1&transaction_id=${tx}&user_id=u`;
  return `${body}&signature=${sign("sha256", Buffer.from(body), privateKey).toString("base64url")}&key_id=1`;
};
const reward = (q: string) => handleReward(q, { db, salt: SALT, getKeys, now: NOW });
const bonusOf = async (deviceId: string) => (await db.doc(`quota/${deviceKey(deviceId, SALT)}`).get()).get("bonus");

beforeEach(() => resetConfigCache());

describe("handleReward", () => {
  it("grants the reward bonus", async () => {
    const dev = newDeviceId();
    expect(await reward(query(dev, randomUUID()))).toBe("granted");
    expect(await bonusOf(dev)).toBe(3);
  });

  it("ignores a duplicate transaction", async () => {
    const dev = newDeviceId();
    const tx = randomUUID();
    await reward(query(dev, tx));
    expect(await reward(query(dev, tx))).toBe("duplicate");
    expect(await bonusOf(dev)).toBe(3);
  });

  it("stops at three rewards per day", async () => {
    const dev = newDeviceId();
    for (let i = 0; i < 3; i++) expect(await reward(query(dev, randomUUID()))).toBe("granted");
    expect(await reward(query(dev, randomUUID()))).toBe("limit");
    expect(await bonusOf(dev)).toBe(9);
  });

  it("rejects an unsigned callback", async () => {
    await expect(reward(`custom_data=${newDeviceId()}&transaction_id=x`)).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });
});
