# SnapBrain v1 — Design Spec

- **Tanggal:** 2026-09-29
- **Status:** Draft, menunggu review
- **Sumber:** PRD "SnapBrain (Screenshot-to-Action AI)" + sesi brainstorming 2026-09-29

## 1. Tujuan & kriteria sukses

**Tujuan:** App Android yang siap rilis di Play Store (Indonesia) dan mengubah screenshot jadi data yang bisa ditindaklanjuti (kategori, info kunci, tombol aksi, daftar tugas). Monetisasi iklan + langganan aktif sejak hari pertama.

**Kriteria sukses teknis v1:**
- Share → hasil tampil di sheet: p50 ≤ 3 detik di 4G (target dari PRD; harus diukur, belum terbukti).
- Tidak ada screenshot yang hilang: setiap share tersimpan lokal sebelum ada panggilan jaringan.
- 100% respons `extract` yang sampai ke app lolos validasi skema.
- Kuota, status premium, dan reward hanya ditentukan server. Klaim dari app tidak pernah dipercaya.

**Kriteria bisnis:** [UNKNOWN] Belum ada baseline. Ditetapkan setelah ada data dari minggu pertama rilis.

## 2. Log keputusan

| # | Keputusan | Alasan |
|---|---|---|
| D1 | Target: siap Play Store, monetisasi sejak hari pertama | Pilihan user |
| D2 | Tanpa akun & tanpa cloud sync di v1. Sync pindah ke v1.1 | Memangkas auth, sync, dan beban privasi. Premium v1 = bebas iklan + kuota AI + Task Planner |
| D3 | Kotlin + Jetpack Compose, Android-only | Semua fitur inti adalah API native Android |
| D4 | Backend Firebase (Cloud Functions TS, Firestore, Anonymous Auth, App Check + Play Integrity) | Satu ekosistem, tanpa server yang harus dikelola |
| D5 | LLM via API. Endpoint/model disimpan di config. Model launch dipilih lewat eval (§10). Self-host dievaluasi ulang setelah ada data volume | Biaya per request lebih murah di volume awal yang belum pasti |
| D6 | Kuota: Free 15/bulan, Premium 300/bulan (bukan 1000). Disimpan di dokumen Firestore `config/app` (bisa diubah dari Console tanpa deploy/rilis). Juga menyimpan model & effort LLM | FUP 1000 berpotensi rugi per user (lihat catatan biaya di §11) |
| D7 | Reward video: +3 proses per video, maks 3 video/hari, diverifikasi server | Default yang disetujui |
| D8 | Alur share hibrida: simpan dulu, coba sinkron (timeout 6 dtk), fallback ke antrian background | UX instan + tidak ada data hilang |
| D9 | Kuota dicatat per `deviceKey`, bukan per uid. App mengirim `device_id` = SHA-256(`ANDROID_ID`); server menghitung `deviceKey` = SHA-256(`device_id` + salt rahasia) | uid anonim berubah saat reinstall. `ANDROID_ID` stabil lintas reinstall di Android 8+ |
| D10 | Distribusi v1 hanya di Play Store Indonesia | Menghindari kewajiban consent EEA/UK (UMP) di v1 |

## 3. Scope

**Masuk v1:** Share intent + BottomSheet, OCR on-device, extract via backend, Inbox + filter kategori + search lokal, Detail + zoom + Task Planner, antrian offline, iklan banner + rewarded, langganan Play Billing, paywall, dark mode, UI Bahasa Indonesia.

**Tidak masuk v1:** akun/login, cloud sync, iOS, self-hosted LLM, multi-bahasa UI, edit manual hasil AI, export/backup (kecuali Android Auto Backup bawaan untuk database lokal).

## 4. Arsitektur

### 4.1 Android (satu modul `app`, package per fitur)

| Komponen | Tugas |
|---|---|
| `ShareActivity` | Activity translucent + `ModalBottomSheet`. Menerima `ACTION_SEND` `image/*` |
| `ImageStore` | Menyalin gambar ke `filesDir/images/{id}.jpg` (dikompres, sisi terpanjang maks 2048px) |
| `OcrEngine` | ML Kit Text Recognition (Latin), **model bundled**, agar share pertama tetap jalan offline |
| `ExtractApi` | Memanggil callable function `extract` |
| `ItemRepository` | Room + tabel FTS4 untuk search |
| `ProcessWorker` | WorkManager, constraint `CONNECTED`, backoff eksponensial |
| `MainActivity` | Inbox, Search, Detail (Navigation Compose) |
| `Entitlement` | Play Billing Library + cache status premium dari server |
| `Ads` | AdMob banner + rewarded |
| `AppContainer` | DI manual (satu object). Tanpa Hilt |

Min SDK 26. Target SDK mengikuti syarat Play Store saat rilis ([UNKNOWN] levelnya dicek saat rilis).

### 4.2 Backend (Firebase, Cloud Functions TypeScript)

| Function | Tugas |
|---|---|
| `extract` (callable) | Wajib Auth + App Check → validasi input → cek kuota → panggil LLM → validasi skema (Zod) → **potong kuota hanya jika sukses** (transaksi Firestore) → untuk free: `tasks` dipotong ke 1 |
| `verifyPurchase` (callable) | Verifikasi purchase token via Google Play Developer API → set `premiumUntil` → acknowledge pembelian |
| `playRtdn` (Pub/Sub) | Real-time Developer Notifications: perpanjang, batal, refund → update `premiumUntil` |
| `adReward` (HTTP) | Callback AdMob Server-Side Verification → verifikasi signature → +3 bonus (cek batas 3/hari) |

`llm.ts` adalah satu-satunya modul yang tahu provider LLM. Isinya satu fungsi `extractFromText(text): Promise<ExtractData>`. Provider/model/endpoint dibaca dari config.

## 5. Data

### 5.1 Room (lokal)

```
Item
  id            TEXT PK (UUID)
  createdAt     INTEGER (epoch ms)
  imagePath     TEXT
  ocrText       TEXT
  status        TEXT   -- UNPROCESSED | DONE | QUOTA_BLOCKED | FAILED
  category      TEXT?  -- task|finance|shopping|event|reference|unclassified
  title         TEXT?
  extractedInfo TEXT?  -- JSON object string
  actionType    TEXT?  -- track_parcel|add_calendar|copy_text|open_url|none
  actionPayload TEXT?
  tasks         TEXT?  -- JSON array string [{id,description,is_completed}]
  tasksTotal    INTEGER DEFAULT 0
  attempts      INTEGER DEFAULT 0

ItemFts (FTS4, contentEntity = Item): title, ocrText, category
```

Inbox: `ORDER BY createdAt DESC`. Search: `MATCH` query di `ItemFts` + filter kategori.

### 5.2 Firestore

```
quota/{deviceKey}
  month         "2026-09"   -- bulan berjalan zona Asia/Jakarta; jika beda: used & bonus di-reset
  used          number
  bonus         number      -- dari reward video, hangus di akhir bulan
  rewardsDay    "2026-09-29"
  rewardsToday  number
  premiumUntil  number?     -- epoch ms
```

Limit efektif = (`premiumUntil > now` ? `limitPremium` : `limitFree`) + `bonus`.

```
quota/{deviceKey}/charges/{itemId}_{f|p}   -- penanda item sudah ditagih (idempotensi), expireAt 40 hari (TTL)
rewards/{sha256(transaction_id)}          -- idempotensi callback AdMob, expireAt 40 hari (TTL)
purchases/{sha256(purchaseToken)}         -- { deviceKey }
config/app                                -- limitFree, limitPremium, rewardAmount, rewardMaxPerDay, llmModel, llmEffort
```

Satu purchase token hanya terhubung ke **satu** `deviceKey`. Restore di HP baru akan memindahkan premium ke HP itu.

Security rules: semua koleksi `read, write: if false`. Hanya Functions (Admin SDK) yang bisa mengakses.

**Privasi:** teks OCR **tidak disimpan dan tidak di-log** di server. Log hanya berisi metadata (latency, kategori, kode error).

## 6. API contract `extract`

**Request:** `{ ocr_text: string, device_id: string, item_id: string (UUID) }`. `item_id` membuat penagihan idempoten: retry item yang sama (misal app timeout 6 dtk tapi server sukses, lalu `ProcessWorker` mengulang) tidak dipotong kuota dua kali. Ditagih ulang hanya jika tier berubah (proses ulang setelah upgrade, §8). Ditolak dengan `invalid-argument` jika kosong atau > 20.000 karakter.

**Response sukses:**
```json
{
  "data": {
    "category": "task | finance | shopping | event | reference | unclassified",
    "title": "maks 5 kata",
    "extracted_info": { "key": "value" },
    "action_type": "track_parcel | add_calendar | copy_text | open_url | none",
    "action_payload": "string",
    "tasks": [{ "id": 1, "description": "string", "is_completed": false }]
  },
  "tasks_total": 5,
  "quota": { "used": 7, "limit": 15 }
}
```

Perubahan dari PRD:
- Wrapper `status` dihapus, karena error pakai kode `HttpsError` dari callable.
- Ditambah `tasks_total`, agar free user tahu ada berapa tugas yang terkunci.
- Ditambah `quota`, agar UI bisa menampilkan sisa kuota.

**Error:**

| Kode | Arti | Kuota dipotong? |
|---|---|---|
| `unauthenticated` / `failed-precondition` | Auth atau App Check gagal | Tidak |
| `invalid-argument` | Input tidak valid | Tidak |
| `resource-exhausted` | Kuota habis | Tidak |
| `unavailable` | LLM error/timeout, atau output tidak lolos skema setelah 1x retry | Tidak |

## 7. Alur share (hibrida)

1. `ShareActivity` menerima URI → `ImageStore` menyalin gambar → OCR → insert `Item(status=UNPROCESSED)`. Langkah ini selalu jalan, offline pun.
2. Jika OCR menghasilkan < 10 karakter: tidak memanggil API, set `category=unclassified, status=DONE`, kuota tidak terpakai.
3. Jika online: panggil `extract` dengan timeout 6 detik. Teks loading sesuai PRD.
   - Sukses → update item → tampilkan `ResultCard`.
   - `resource-exhausted` → `status=QUOTA_BLOCKED` → tampilkan paywall sheet: [Upgrade Pro] [Tonton Video +3] [Nanti]. Item tetap tersimpan.
   - Timeout/offline/`unavailable`/sheet ditutup → tetap `UNPROCESSED` → enqueue `ProcessWorker` → sheet menampilkan "Tersimpan, diproses otomatis".
4. Tombol pada `ResultCard`: **Simpan** (default) dan **Simpan & Hapus Asli**. Tombol kedua hanya muncul jika API ≥ 30 dan URI berasal dari MediaStore; penghapusan lewat `MediaStore.createDeleteRequest` (dialog konfirmasi sistem). Selain itu hanya ada tombol **Simpan**. (`ponytail:` API 26–29 dilewati; tambahkan jika ternyata banyak user di versi itu.)
5. `ProcessWorker` memproses semua `UNPROCESSED`. `unavailable` → retry dengan backoff, maks 5 percobaan → `FAILED` (ada tombol "Coba lagi" di Detail). `resource-exhausted` → `QUOTA_BLOCKED`, tidak di-retry sampai kuota tersedia lagi (bulan baru, upgrade, atau reward).

**Aksi dinamis:**

| `action_type` | Aksi |
|---|---|
| `track_parcel` | Buka pencarian web berisi nomor resi (`action_payload`) |
| `add_calendar` | Intent `ACTION_INSERT` ke Calendar |
| `copy_text` | Salin ke clipboard |
| `open_url` | Buka browser. Hanya skema `https`/`http` |
| `none` | Tidak ada tombol |

## 8. Monetisasi

- **Langganan:** produk Play `premium_monthly` Rp19.000/bulan. Alurnya: beli → app mengirim token + `device_id` ke `verifyPurchase` → server memverifikasi, set `premiumUntil`, lalu acknowledge. Saat app dibuka: `queryPurchasesAsync` → jika ada token yang belum terhubung ke `deviceKey` ini, verifikasi ulang (untuk restore).
- **Banner:** AdMob di Inbox pada posisi ke-3 `LazyColumn`, hanya untuk free.
- **Rewarded:** AdMob dengan SSV. `custom_data = device_id`; server menurunkan `deviceKey` dengan cara yang sama. Bonus hanya ditambah oleh callback `adReward`, bukan oleh app.
- **Task Planner (free):** server hanya mengirim 1 task. Detail menampilkan task pertama + placeholder blur sejumlah `tasks_total - 1` + CTA "🔒 Buka AI Task Planner — Upgrade Pro". Setelah upgrade, membuka item yang terkunci akan menjalankan `extract` ulang (memakai 1 kuota premium).
- **Paywall** muncul di 3 tempat: kuota habis (alur share), ikon mahkota di TopAppBar, dan CTA Task Planner.

## 9. UI

Mengikuti PRD bagian "UI / UX Component Architecture" (Inbox, SmartCard, Detail) dengan Material 3, dark mode, dan warna primer teal. Tambahan dari spec ini:
- Badge status di SmartCard untuk `UNPROCESSED` ("Menunggu internet"), `QUOTA_BLOCKED` ("Kuota habis"), dan `FAILED` ("Gagal, coba lagi").
- Sisa kuota ditampilkan di layar paywall.

## 10. Testing & eval

**Kode:**
- Unit test (JVM): pemetaan `action_type` → aksi, parsing respons, logika status item, logika reset bulan/kuota di Functions.
- Firebase Emulator: `extract` (kuota sukses/habis/gagal-tidak-dipotong), `adReward` (batas 3/hari), security rules menolak akses dari klien.
- Instrumented: query FTS di Room.
- Manual matrix sebelum rilis: share dari Galeri, WhatsApp, Chrome, dan File manager; mode pesawat; kuota habis; beli, batal, restore (license tester Play).

**Eval model (sebelum rilis):**
- 50–100 screenshot asli Indonesia (resi, struk/transfer, chat berisi tugas, poster event, resep) dilabeli manual (kategori, action_type, payload kunci).
- Kandidat: model API dari minimal 2 tingkat harga.
- Metrik: validitas JSON, akurasi kategori, akurasi `action_type` + payload, latency p50/p95, biaya per request.
- Aturan pilih: model **termurah** yang memenuhi ambang. Ambang yang diusulkan: JSON valid 100% (setelah retry), kategori ≥ 90%, action ≥ 85%, p50 ≤ 2 dtk di sisi backend. Angka ambang masih usulan dan bisa diubah user.

## 11. Risiko & catatan

- **Biaya LLM per request:** [UNKNOWN] Final setelah eval. Estimasi awal dengan harga API Claude (1.500 token input / 400 output): Haiku 4.5 ~$0,0035, Sonnet 5.5 ~$0,007+, Opus 5.5 ~$0,014+. Pendapatan bersih premium ±$1/bulan (asumsi kurs ±Rp16.500/USD, potongan Play ±15%). Kuota harus dicek ulang terhadap biaya aktual model terpilih.
- **Kompetisi:** Pixel Screenshots, Circle to Search, dan fitur ekstraksi teks di Samsung Gallery. [UNKNOWN] Seberapa besar dampaknya ke pasar target.
- **Abuse kuota:** `ANDROID_ID` bisa diakali di perangkat root. Diterima di v1 (`ponytail:` upgrade ke atestasi per perangkat jika abuse terlihat di data).
- **Latency cold start Cloud Functions:** [UNKNOWN] Diukur saat eval. Jika melanggar target, set `minInstances: 1` untuk `extract`.
- **Kepatuhan Play:** isi Data Safety (teks OCR dikirim ke server untuk diproses, tidak disimpan) dan kebijakan privasi wajib ada sebelum submit.
