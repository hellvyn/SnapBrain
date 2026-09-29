# SnapBrain — Backend Cloudflare Workers (revisi backend v1)

- **Tanggal:** 2026-09-29
- **Status:** Draft, menunggu review
- **Menggantikan bagian dari:** `docs/superpowers/specs/2026-09-29-snapbrain-v1-design.md`, yaitu D4, D5, §4.2, §5.2 dan bagian RTDN di §8. Semua bagian lain spec v1 tetap berlaku.

## 1. Tujuan

Backend yang bisa jalan **tanpa biaya dan tanpa kartu kredit** di tahap tes dan awal rilis, dengan LLM milik user sendiri. Perilaku yang terlihat oleh app tetap sama seperti spec v1:
- kuota
- penagihan idempoten per `item_id`
- task terkunci untuk free tier
- reward video terverifikasi
- entitlement premium

## 2. Log keputusan

| # | Keputusan | Alasan |
|---|---|---|
| C1 | Backend = **Cloudflare Workers + D1**, menggantikan Cloud Functions + Firestore | Plan gratis tanpa kartu. Cloud Functions mewajibkan Firebase Blaze |
| C2 | **Firebase tetap dipakai di plan Spark** untuk Auth anonim + App Check saja | Gratis tanpa kartu. Token keduanya adalah JWT yang bisa diverifikasi di Worker, jadi keamanan dan identitas tidak perlu dibangun ulang |
| C3 | LLM = proxy kompatibel OpenAI milik user: `POST {LLM_BASE_URL}/chat/completions`, base URL `https://freellm.hellvyn.id/v1` | Tanpa biaya API Claude. Endpoint standar, cukup dipanggil dengan `fetch`, tanpa SDK |
| C4 | RTDN (Pub/Sub) dihapus. Status langganan dicek ulang tiap app dibuka + cron harian di Worker | Pub/Sub kemungkinan butuh billing GCP. Konsekuensinya refund/pembatalan terdeteksi paling lambat ±1 hari |
| C5 | `functions/`, `firebase.json` bagian functions, `firestore.rules` dan `backend.yml` dihapus | Tidak merawat dua backend |
| C6 | Logika murni backend lama di-port **beserta test-nya** | Sudah teruji (60 test). Yang berubah hanya lapisan data dan HTTP |
| C7 | Deploy otomatis dari GitHub Actions saat merge ke `main` | User tidak perlu CLI atau laptop |

**Yang belum terverifikasi:**
- [UNKNOWN] Batas plan gratis Workers/D1 saat ini (perkiraan: ±100 ribu request/hari, CPU ±10 ms per request, di luar waktu menunggu `fetch`). Wajib dicek user di halaman harga Cloudflare.
- [UNKNOWN] Nama model chat yang tersedia di proxy user. Default config `auto`.
- [UNKNOWN] Apakah pembuatan service account + Play Developer API butuh billing GCP aktif. Baru relevan di Rencana 3.

## 3. Arsitektur

```
Android app ──HTTPS──▶ Cloudflare Worker (snapbrain-api)
  headers: Authorization: Bearer <Firebase ID token>
           X-Firebase-AppCheck: <App Check token>
                         ├─ D1 (SQLite): quota, charges, rewards, purchases
                         ├─ fetch → LLM proxy /chat/completions
                         ├─ fetch → Google Play Developer API (service account)
                         └─ Cron harian → cek ulang langganan
AdMob ──GET callback──▶ Worker /ad-reward (signature ECDSA)
```

### 3.1 Route

| Route | Auth | Tugas |
|---|---|---|
| `POST /extract` | ID token + App Check | Validasi input → cek kuota → LLM → validasi skema → potong kuota secara idempoten (hanya jika sukses) → free tier: task dipotong ke 1 |
| `POST /verify-purchase` | ID token + App Check | Verifikasi token Play → set `premium_until` + `premium_token` → acknowledge |
| `GET /ad-reward` | Signature SSV AdMob | Verifikasi signature → harus `ad_unit == ADMOB_AD_UNIT_ID` → +bonus, maks per hari, idempoten per `transaction_id` |
| Cron `0 18 * * *` (01:00 WIB) | — | Cek ulang semua `purchases` yang `premium_until` ≥ sekarang − 3 hari, lalu perbarui entitlement |

**Kontrak request/response `/extract`** sama dengan spec v1 §6 (`ocr_text`, `device_id`, `item_id` → `data`, `tasks_total`, `quota`).

**Error:** HTTP status dengan body `{ "error": "<CODE>" }`, dengan `CODE` ∈ `INVALID_ARGUMENT` (400), `UNAUTHENTICATED` (401), `FAILED_PRECONDITION` (403, App Check), `RESOURCE_EXHAUSTED` (429), `UNAVAILABLE` (503), `INTERNAL` (500). Nama kode sama dengan `FirebaseFunctionsException.Code`, sehingga `core` di app tidak berubah.

### 3.2 Verifikasi token

- **Firebase ID token:** JWT RS256.
  - JWKS: `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`
  - `iss` = `https://securetoken.google.com/<FIREBASE_PROJECT_ID>`, `aud` = `<FIREBASE_PROJECT_ID>`
- **App Check token:** JWT RS256.
  - JWKS: `https://firebaseappcheck.googleapis.com/v1/jwks`
  - `iss` = `https://firebaseappcheck.googleapis.com/<FIREBASE_PROJECT_NUMBER>`
  - `aud` berisi `projects/<FIREBASE_PROJECT_NUMBER>`
- **Library:** `jose` (WebCrypto, jalan di Workers). JWKS di-cache oleh `createRemoteJWKSet`.
- Nilai JWKS/issuer di atas berasal dari dokumentasi Firebase sesuai pengetahuan saat ini. Implementasi wajib memverifikasinya dengan test yang memakai token buatan sendiri, dan dengan uji di perangkat.

### 3.3 Data (D1)

```sql
CREATE TABLE quota (
  device_key     TEXT PRIMARY KEY,
  month          TEXT NOT NULL,           -- 'YYYY-MM' WIB
  used           INTEGER NOT NULL DEFAULT 0,
  bonus          INTEGER NOT NULL DEFAULT 0,
  rewards_day    TEXT NOT NULL,           -- 'YYYY-MM-DD' WIB
  rewards_today  INTEGER NOT NULL DEFAULT 0,
  premium_until  INTEGER,                 -- epoch ms
  premium_token  TEXT                     -- sha256(purchase token) pemberi premium
);
CREATE TABLE charges (
  device_key TEXT NOT NULL,
  charge_id  TEXT NOT NULL,               -- '<item_id>_<f|p>'
  expire_at  INTEGER NOT NULL,
  PRIMARY KEY (device_key, charge_id)
);
CREATE TABLE rewards (
  tx_hash   TEXT PRIMARY KEY,             -- sha256(transaction_id)
  granted   INTEGER NOT NULL,
  expire_at INTEGER NOT NULL
);
CREATE TABLE purchases (
  token_hash    TEXT PRIMARY KEY,         -- sha256(purchase token)
  token         TEXT NOT NULL,            -- dibutuhkan cron untuk cek ulang
  device_key    TEXT NOT NULL,
  premium_until INTEGER
);
```

- **Atomisitas:** D1 `batch()` dijalankan sebagai satu transaksi berurutan.
  - Penagihan: `INSERT OR IGNORE INTO charges ...`, lalu `UPDATE quota SET used = used + 1 WHERE device_key = ? AND changes() = 1`.
  - Reward dan perpindahan premium antar device memakai pola yang sama: satu batch per operasi.
- **Kedaluwarsa:** baris `charges`/`rewards` yang lewat `expire_at` (40 hari) dihapus oleh cron harian yang sama. D1 tidak punya TTL.
- **Privasi:** teks OCR tidak disimpan dan tidak di-log. Log hanya berisi kode error.

### 3.4 Config

| Nama | Jenis | Default |
|---|---|---|
| `LIMIT_FREE`, `LIMIT_PREMIUM`, `REWARD_AMOUNT`, `REWARD_MAX_PER_DAY` | var | 15, 300, 3, 3 |
| `LLM_BASE_URL` | var | `https://freellm.hellvyn.id/v1` |
| `LLM_MODEL` | var | `auto` |
| `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER` | var | `snapbrain-hellvyn`, `472964840390` |
| `PACKAGE_NAME` | var | `com.snapbrain.app` |
| `ADMOB_AD_UNIT_ID` | var | kosong. Selama kosong, semua reward ditolak (fail-closed) |
| `LLM_API_KEY`, `DEVICE_SALT` | secret | diisi user di dashboard Cloudflare |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | diisi di Rencana 3. Selama kosong, `/verify-purchase` menjawab `UNAVAILABLE` |

Var tinggal di `worker/wrangler.jsonc` dan berubah lewat commit (deploy CI menimpa nilai di dashboard). Hanya secret yang diisi di dashboard Cloudflare.

### 3.5 LLM

- **Request:** `POST {LLM_BASE_URL}/chat/completions` dengan header `Authorization: Bearer {LLM_API_KEY}`. Body:
  - `model`
  - `messages`: system prompt yang sama dengan backend lama, lalu user `<ocr>…</ocr>`
  - `temperature: 0`
  - `response_format: {"type":"json_object"}`
- **Parsing:** `choices[0].message.content` di-parse sebagai JSON, lalu divalidasi dengan skema zod lama (`LlmOutput`). Blok ```` ```json ```` di awal/akhir dibuang sebelum parse.
- **Retry:** gagal HTTP/parse/skema → retry 1x. Jika percobaan pertama mendapat HTTP 400, retry dilakukan **tanpa** `response_format`, untuk proxy yang tidak mendukungnya. Gagal dua kali → `UNAVAILABLE`, kuota tidak dipotong.
- **Timeout:** 25 detik per percobaan.

## 4. Perubahan app Android

- `ExtractClient` memanggil `POST {API_BASE_URL}/extract` dengan `HttpURLConnection` di `Dispatchers.IO`, tanpa dependency baru.
  - Header diambil dari `Firebase.auth.currentUser.getIdToken(false)` dan `Firebase.appCheck.getAppCheckToken(false)`.
  - Status non-2xx dipetakan dari body `error` ke `outcomeOfCode`. Error jaringan menjadi `Retryable`.
- `API_BASE_URL` diset lewat `buildConfigField` dari properti Gradle `snapbrainApiUrl`, dengan default URL `workers.dev` hasil deploy. Nilainya diisi setelah deploy pertama.
- Dependency `firebase-functions` dihapus. `firebase-auth` dan `firebase-appcheck-*` tetap dipakai.
- Tidak ada perubahan di `core`, UI, Room, maupun worker.

## 5. CI / deploy

- `.github/workflows/worker.yml`:
  - Setiap PR yang menyentuh `worker/**`: `npm ci`, `tsc`, lalu `vitest` (pool Workers, D1 lokal).
  - Push ke `main`: menjalankan test, lalu `wrangler d1 migrations apply snapbrain --remote` dan `wrangler deploy`.
  - Secret GitHub yang dipakai: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.
- Langkah manual user (panduan di `docs/cloudflare-ops.md`):
  1. Buat akun Cloudflare.
  2. Buat database D1 bernama `snapbrain` dan kirim `database_id`-nya (bukan rahasia).
  3. Buat API token (template "Edit Cloudflare Workers" + izin D1 Edit).
  4. Simpan token dan Account ID ke secret GitHub.
  5. Setelah deploy pertama, isi secret Worker `LLM_API_KEY` dan `DEVICE_SALT` di dashboard.
  6. Di Firebase Console (Spark): aktifkan Anonymous Auth dan App Check.

## 6. Testing

- Test logika murni lama (kuota, skema, device, reward-signature, entitlement) di-port, dengan assertion yang sama.
- Test handler memakai D1 lokal (`@cloudflare/vitest-pool-workers`). Kasus yang dicakup sama dengan test emulator lama:
  - idempotensi charge
  - kuota habis tanpa memanggil LLM
  - LLM gagal → tidak ditagih
  - premium
  - reset bulan
  - reward duplikat / batas harian / ad unit lain
  - pindah device
  - token lama tidak menghapus premium baru
  - cron memperbarui entitlement
- Test verifikasi token memakai pasangan kunci RSA buatan test dan JWKS lokal. Mencakup: token valid, `aud`/`iss` salah, token kedaluwarsa, header hilang.
- Test adapter LLM memakai `fetch` tiruan. Mencakup: JSON valid, JSON dalam blok ```` ``` ````, output invalid lalu retry, 400 lalu retry tanpa `response_format`, gagal dua kali → `UNAVAILABLE`, pesan error tidak memuat teks OCR.

## 7. Risiko

- Proxy LLM user adalah titik gagal tunggal. Kalau mati, semua item jatuh ke antrian retry (maks 5 percobaan, lalu FAILED + "Coba lagi").
- Kualitas ekstraksi bergantung pada model di balik proxy. Eval (spec v1 §10) tetap berlaku.
- Deteksi refund terlambat ±1 hari (C4).
- Batas CPU plan gratis. Verifikasi dua JWT dan parse JSON diperkirakan jauh di bawah batas, karena waktu menunggu `fetch` tidak dihitung. Wajib diukur setelah deploy.
