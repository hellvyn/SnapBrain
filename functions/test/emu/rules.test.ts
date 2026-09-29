import { readFileSync } from "node:fs";
import { assertFails, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, describe, it } from "vitest";

let env: RulesTestEnvironment;
beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-snapbrain",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
  });
});
afterAll(() => env.cleanup());

describe("firestore rules", () => {
  it("deny every client read and write", async () => {
    const fs = env.authenticatedContext("u1").firestore();
    await assertFails(getDoc(doc(fs, "quota/x")));
    await assertFails(setDoc(doc(fs, "quota/x"), { used: 0 }));
    await assertFails(getDoc(doc(fs, "config/app")));
    await assertFails(setDoc(doc(fs, "purchases/x"), { deviceKey: "me" }));
  });
});
