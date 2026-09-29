import type Anthropic from "@anthropic-ai/sdk";
import { describe, expect, it, vi } from "vitest";
import { createExtractor, LlmUnavailable } from "../../src/llm";

const VALID = {
  category: "finance",
  title: "Transfer ke Budi",
  extracted_info: [{ key: "Total", value: "Rp 50.000" }],
  action_type: "copy_text",
  action_payload: "1234567890",
  tasks: [],
};
const CFG = { llmModel: "claude-opus-5-5", llmEffort: "low" as const };

function fakeClient(...results: unknown[]) {
  const parse = vi.fn();
  for (const r of results) {
    if (r instanceof Error) parse.mockRejectedValueOnce(r);
    else parse.mockResolvedValueOnce(r);
  }
  return { client: { messages: { parse } } as unknown as Anthropic, parse };
}

describe("createExtractor", () => {
  it("returns mapped data on the first valid response", async () => {
    const { client, parse } = fakeClient({ stop_reason: "end_turn", parsed_output: VALID });
    const r = await createExtractor(client)("Transfer Rp 50.000", CFG);
    expect(r.extracted_info).toEqual({ Total: "Rp 50.000" });
    expect(parse).toHaveBeenCalledTimes(1);
    const params = parse.mock.calls[0][0];
    expect(params).toMatchObject({ model: "claude-opus-5-5", output_config: { effort: "low" } });
    expect(params.messages[0].content).toBe("<ocr>\nTransfer Rp 50.000\n</ocr>");
  });

  it("omits effort when llmEffort is empty", async () => {
    const { client, parse } = fakeClient({ stop_reason: "end_turn", parsed_output: VALID });
    await createExtractor(client)("x", { llmModel: "claude-haiku-4-5", llmEffort: "" });
    expect(parse.mock.calls[0][0].output_config).not.toHaveProperty("effort");
  });

  it("retries once when the output does not parse", async () => {
    const { client, parse } = fakeClient(
      { stop_reason: "end_turn", parsed_output: null },
      { stop_reason: "end_turn", parsed_output: VALID },
    );
    await expect(createExtractor(client)("x", CFG)).resolves.toMatchObject({ category: "finance" });
    expect(parse).toHaveBeenCalledTimes(2);
  });

  it("retries once on refusal", async () => {
    const { client, parse } = fakeClient(
      { stop_reason: "refusal", parsed_output: null },
      { stop_reason: "end_turn", parsed_output: VALID },
    );
    await createExtractor(client)("x", CFG);
    expect(parse).toHaveBeenCalledTimes(2);
  });

  it("throws LlmUnavailable after two failures", async () => {
    const { client, parse } = fakeClient(new Error("timeout"), new Error("timeout"));
    await expect(createExtractor(client)("x", CFG)).rejects.toBeInstanceOf(LlmUnavailable);
    expect(parse).toHaveBeenCalledTimes(2);
  });
});
