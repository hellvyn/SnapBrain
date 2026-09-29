import { defineConfig } from "vitest/config";

// Emulator tests share one Firestore (and config/app), so run files one at a time.
export default defineConfig({ test: { fileParallelism: false } });
