import { describe, expect, it, vi } from "vitest";
import { createExtractor, LlmUnavailable, parseJsonContent } from "../src/llm";

const VALID = {
  category: "finance",
  title: "Transfer ke Budi",
  extracted_info: [{ key: "Total", value: "Rp 50.000" }],
  action_type: "copy_text",
  action_payload: "1234567890",
  tasks: [],
};
const CFG = { baseUrl: "https://llm.test/v1/", model: "auto", apiKey: "k" };
const reply = (content: string, status = 200) =>
  new Response(JSON.stringify({ choices: [{ message: { content } }] }), { status });

function fakeFetch(...responses: (Response | Error)[]) {
  const fn = vi.fn<typeof fetch>();
  for (const r of responses) {
    if (r instanceof Error) fn.mockRejectedValueOnce(r);
    else fn.mockResolvedValueOnce(r);
  }
  return fn;
}

describe("createExtractor", () => {
  it("posts an OpenAI chat request and maps the result", async () => {
    const f = fakeFetch(reply(JSON.stringify(VALID)));
    const r = await createExtractor(CFG, f)("Transfer Rp 50.000");
    expect(r.extracted_info).toEqual({ Total: "Rp 50.000" });
    const [url, init] = f.mock.calls[0];
    expect(url).toBe("https://llm.test/v1/chat/completions");
    expect((init?.headers as Record<string, string>).authorization).toBe("Bearer k");
    const body = JSON.parse(init?.body as string);
    expect(body).toMatchObject({ model: "auto", temperature: 0, response_format: { type: "json_object" } });
    expect(body.messages[1].content).toBe("<ocr>\nTransfer Rp 50.000\n</ocr>");
  });

  it("accepts JSON wrapped in a markdown fence", async () => {
    const f = fakeFetch(reply("```json\n" + JSON.stringify(VALID) + "\n```"));
    await expect(createExtractor(CFG, f)("x")).resolves.toMatchObject({ category: "finance" });
  });

  it("retries once when the output does not match the schema", async () => {
    const f = fakeFetch(reply('{"category":"gossip"}'), reply(JSON.stringify(VALID)));
    await expect(createExtractor(CFG, f)("x")).resolves.toMatchObject({ category: "finance" });
    expect(f).toHaveBeenCalledTimes(2);
  });

  it("drops response_format after a 400", async () => {
    const f = fakeFetch(new Response("bad", { status: 400 }), reply(JSON.stringify(VALID)));
    await createExtractor(CFG, f)("x");
    expect(JSON.parse(f.mock.calls[1][1]?.body as string)).not.toHaveProperty("response_format");
  });

  it("throws LlmUnavailable after two failures without leaking text", async () => {
    const f = fakeFetch(new Error("secret OCR text"), new Response("x", { status: 503 }));
    const err = await createExtractor(CFG, f)("secret OCR text").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(LlmUnavailable);
    expect((err as Error).message).not.toContain("secret OCR text");
    expect(f).toHaveBeenCalledTimes(2);
  });
});

describe("parseJsonContent", () => {
  it("returns undefined for non-JSON", () => {
    expect(parseJsonContent("maaf, saya tidak bisa")).toBeUndefined();
  });
});
