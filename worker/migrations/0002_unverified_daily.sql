-- Beta mode: one counter per UTC day for /extract calls that came without a valid App Check token.
CREATE TABLE unverified_daily (
  day   TEXT PRIMARY KEY,
  count INTEGER NOT NULL
);
