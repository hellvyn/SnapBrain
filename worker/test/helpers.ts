export const SALT = "test-salt";
export const NOW = Date.UTC(2026, 8, 29, 5, 0); // 29 Sep 2026 12:00 WIB
export const DAY = 24 * 60 * 60 * 1000;
export const newDeviceId = (): string =>
  [...crypto.getRandomValues(new Uint8Array(32))].map((b) => b.toString(16).padStart(2, "0")).join("");
