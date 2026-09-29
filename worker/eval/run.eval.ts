import { writeFileSync } from "node:fs";
import { expect, it } from "vitest";
import { createExtractor } from "../src/llm";
import { FIXTURES } from "./fixtures";

const RUNS = Number(process.env.EVAL_RUNS ?? 3);
const BASE = process.env.LLM_BASE_URL ?? "https://freellm.hellvyn.id/v1";
const MODEL = process.env.LLM_MODEL ?? "auto";
const CTX = { today: "2026-09-29", tz: "Asia/Jakarta" };

interface Row { id: string; run: number; parsed: boolean; problems: string[]; ms: number; model: string }

const pct = (xs: number[], p: number) => [...xs].sort((a, b) => a - b)[Math.min(xs.length - 1, Math.floor(p * xs.length))] ?? 0;
const rate = (n: number, of: number) => `${n}/${of} (${Math.round((100 * n) / of)}%)`;

it("reports extraction quality through the live model", async () => {
  const rows: Row[] = [];
  for (const f of FIXTURES) {
    for (let run = 1; run <= RUNS; run++) {
      let model = "?";
      // Records which model the router picked; the proxy reports it in _routed_via.
      const tracking: typeof fetch = async (input, init) => {
        const res = await fetch(input, init);
        try {
          const body = (await res.clone().json()) as { model?: string; _routed_via?: { model?: string } };
          model = body._routed_via?.model ?? body.model ?? model;
        } catch {
          // non-JSON error page: keep "?"
        }
        return res;
      };
      const extract = createExtractor({ baseUrl: BASE, model: MODEL, apiKey: process.env.LLM_API_KEY ?? "" }, tracking);
      const started = Date.now();
      try {
        const problems = f.check(await extract(f.ocr, CTX));
        rows.push({ id: f.id, run, parsed: true, problems, ms: Date.now() - started, model });
      } catch (e) {
        rows.push({ id: f.id, run, parsed: false, problems: [`gagal: ${e instanceof Error ? e.message : "unknown"}`], ms: Date.now() - started, model });
      }
    }
  }

  const total = rows.length;
  const ms = rows.map((r) => r.ms);
  const models = new Map<string, number>();
  for (const r of rows) models.set(r.model, (models.get(r.model) ?? 0) + 1);
  const report = [
    `# Eval ekstraksi — ${new Date().toISOString()}`,
    "",
    `Model \`${MODEL}\` · ${FIXTURES.length} contoh × ${RUNS} jalan`,
    "",
    `- Lolos skema: ${rate(rows.filter((r) => r.parsed).length, total)}`,
    `- Lolos cek isi: ${rate(rows.filter((r) => r.problems.length === 0).length, total)}`,
    `- Latensi p50 / p90: ${pct(ms, 0.5)} ms / ${pct(ms, 0.9)} ms`,
    `- Model dari router: ${[...models].map(([m, n]) => `${m} ×${n}`).join(", ")}`,
    "",
    "## Tidak lolos",
    "",
    "| contoh | jalan | model | masalah |",
    "|---|---|---|---|",
    ...rows.filter((r) => r.problems.length > 0).map((r) => `| ${r.id} | ${r.run} | ${r.model} | ${r.problems.join("; ")} |`),
    "",
  ].join("\n");
  writeFileSync(new URL("./report.md", import.meta.url), report);
  console.log(report);
  expect(rows).toHaveLength(FIXTURES.length * RUNS);
});
