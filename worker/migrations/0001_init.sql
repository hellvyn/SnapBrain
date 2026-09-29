CREATE TABLE quota (
  device_key     TEXT PRIMARY KEY,
  month          TEXT NOT NULL,
  used           INTEGER NOT NULL DEFAULT 0,
  bonus          INTEGER NOT NULL DEFAULT 0,
  rewards_day    TEXT NOT NULL,
  rewards_today  INTEGER NOT NULL DEFAULT 0,
  premium_until  INTEGER,
  premium_token  TEXT
);
CREATE TABLE charges (
  device_key TEXT NOT NULL,
  charge_id  TEXT NOT NULL,
  expire_at  INTEGER NOT NULL,
  PRIMARY KEY (device_key, charge_id)
);
CREATE TABLE rewards (
  tx_hash   TEXT PRIMARY KEY,
  granted   INTEGER NOT NULL,
  expire_at INTEGER NOT NULL
);
CREATE TABLE purchases (
  token_hash    TEXT PRIMARY KEY,
  token         TEXT NOT NULL,
  device_key    TEXT NOT NULL,
  premium_until INTEGER
);
CREATE INDEX purchases_premium_until ON purchases (premium_until);
