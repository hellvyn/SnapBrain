import { defineConfig } from "vitest/config";

// Live eval against the real model: not part of `npm test`. Needs network, and LLM_API_KEY unless a proxy adds auth.
export default defineConfig({ test: { include: ["eval/**/*.eval.ts"], testTimeout: 1_800_000 } });
