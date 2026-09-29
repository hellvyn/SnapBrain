# Backend ops (Cloudflare): setup sekali

Semua langkah gratis dan tidak butuh kartu.

1. **Akun Cloudflare:** daftar di https://dash.cloudflare.com/sign-up. Buka **Workers & Pages** sekali; jika diminta, pilih subdomain `*.workers.dev` gratis.
2. **Database D1:** Dashboard → Storage & Databases → D1 → Create → nama `snapbrain`. Salin **Database ID**.
3. **API token:** My Profile → API Tokens → Create Token → template **"Edit Cloudflare Workers"** → tambahkan permission **Account · D1 · Edit** → Create. Salin token-nya (hanya muncul sekali). **Account ID** ada di halaman Workers & Pages (kolom kanan).
4. **GitHub** (repo → Settings → Secrets and variables → Actions):
   - Tab **Secrets**: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.
   - Tab **Variables**: `D1_DATABASE_ID`.
5. **Deploy:** otomatis setiap ada perubahan `worker/**` yang masuk ke `main`. Untuk deploy pertama tanpa perubahan kode, buka tab Actions → workflow `worker` → run terakhir di `main` → **Re-run all jobs**.
6. **Secret Worker:** Dashboard → Workers & Pages → `snapbrain-api` → Settings → Variables and Secrets → Add → type **Secret**:
   - `LLM_API_KEY` = key dari dashboard freellm.
   - `DEVICE_SALT` = teks acak panjang (misalnya 40 karakter campuran). **Jangan pernah diganti** setelah rilis, karena semua kuota akan ter-reset.
7. **URL Worker:** tertulis di halaman Worker, bentuknya `https://snapbrain-api.<subdomain>.workers.dev`. Simpan di GitHub **Variables** sebagai `SNAPBRAIN_API_URL`. URL ini dibaca saat APK dibangun, jadi setelah variabel diisi, jalankan ulang workflow `android`: tab Actions → `android` → **Run workflow**, lalu unduh APK baru dari artifact `snapbrain-debug-apk`. APK yang dibangun sebelum variabel ini diisi menuju `snapbrain-api.invalid`, sehingga semua item gagal.
8. **Firebase Console (plan Spark, gratis):** Authentication → Sign-in method → **Anonymous** → Enable. App Check → daftarkan app Android dengan Play Integrity. Untuk APK debug, pakai **Manage debug tokens**.
9. **Opsional:**
   - `LLM_MODEL` (default `auto`), limit kuota, dan `ADMOB_AD_UNIT_ID` adalah `vars` di `worker/wrangler.jsonc`. Setiap deploy CI menimpanya, jadi jangan diubah lewat dashboard. Ubah di `worker/wrangler.jsonc` lalu merge ke `main` (atau minta Claude yang mengubahnya).
   - Hanya secret yang diisi lewat dashboard: `LLM_API_KEY`, `DEVICE_SALT`, dan `PLAY_SERVICE_ACCOUNT_JSON` (baru dibutuhkan di Rencana 3).

## Smoke test setelah deploy pertama

- Kirim `item_id` yang sama dua kali ke `/extract`: kuota hanya berkurang sekali.
- Kirim `transaction_id` reward yang sama dua kali: reward hanya dikreditkan sekali.
- Reward ke-4 di hari yang sama ditolak.
- Setelah iklan ada (AdMob baru terpasang di app pada Rencana 3): tonton satu rewarded ad sungguhan, lalu pastikan log Worker memuat `{"route":"ad-reward","result":"granted"}`.
