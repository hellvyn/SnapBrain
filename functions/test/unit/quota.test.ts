import { describe, expect, it } from "vitest";
import { applyReward, hasQuota, isPremium, jakartaDate, limitOf, monthOf, normalize } from "../../src/quota";

const L = { limitFree: 15, limitPremium: 300, rewardAmount: 3, rewardMaxPerDay: 3 };
const NOW = Date.UTC(2026, 8, 29, 5, 0); // 29 Sep 2026 12:00 WIB

describe("jakarta calendar", () => {
  it("uses UTC+7", () => {
    expect(jakartaDate(Date.UTC(2026, 8, 30, 16, 59))).toBe("2026-09-30");
    expect(jakartaDate(Date.UTC(2026, 8, 30, 17, 0))).toBe("2026-10-01");
    expect(monthOf(Date.UTC(2026, 8, 30, 17, 0))).toBe("2026-10");
  });
});

describe("normalize", () => {
  it("fills defaults for a new device", () => {
    expect(normalize(undefined, NOW)).toEqual({
      month: "2026-09", used: 0, bonus: 0, rewardsDay: "2026-09-29", rewardsToday: 0, premiumUntil: null,
    });
  });

  it("resets used and bonus on a new month but keeps premium", () => {
    const d = normalize(
      { month: "2026-08", used: 15, bonus: 6, rewardsDay: "2026-08-31", rewardsToday: 3, premiumUntil: NOW + 1000 },
      NOW,
    );
    expect(d).toMatchObject({ month: "2026-09", used: 0, bonus: 0, rewardsToday: 0, premiumUntil: NOW + 1000 });
  });

  it("resets only the daily reward counter on a new day", () => {
    const d = normalize(
      { month: "2026-09", used: 4, bonus: 3, rewardsDay: "2026-09-28", rewardsToday: 3, premiumUntil: null },
      NOW,
    );
    expect(d).toMatchObject({ used: 4, bonus: 3, rewardsDay: "2026-09-29", rewardsToday: 0 });
  });
});

describe("limits", () => {
  const base = normalize(undefined, NOW);

  it("adds bonus to the free limit", () => {
    expect(limitOf({ ...base, bonus: 3 }, L, NOW)).toBe(18);
  });

  it("is premium only while premiumUntil is in the future", () => {
    expect(isPremium({ ...base, premiumUntil: NOW + 1 }, NOW)).toBe(true);
    expect(isPremium({ ...base, premiumUntil: NOW }, NOW)).toBe(false);
    expect(limitOf({ ...base, premiumUntil: NOW + 1 }, L, NOW)).toBe(300);
  });

  it("has no quota at the limit", () => {
    expect(hasQuota({ ...base, used: 14 }, L, NOW)).toBe(true);
    expect(hasQuota({ ...base, used: 15 }, L, NOW)).toBe(false);
  });
});

describe("applyReward", () => {
  const base = normalize(undefined, NOW);

  it("adds bonus and counts the reward", () => {
    expect(applyReward(base, L)).toMatchObject({ bonus: 3, rewardsToday: 1 });
  });

  it("returns null after the daily max", () => {
    expect(applyReward({ ...base, rewardsToday: 3 }, L)).toBeNull();
  });
});
