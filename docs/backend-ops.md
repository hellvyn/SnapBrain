# Backend ops: setup sekali per project

1. **Project Firebase** di plan Blaze (secrets dan akses jaringan keluar butuh Blaze). Aktifkan Firestore (mode production), Authentication → Anonymous, dan App Check → Play Integrity untuk app Android.
2. **Secrets:**
   ```bash
   firebase functions:secrets:set ANTHROPIC_API_KEY
   openssl rand -hex 32 | firebase functions:secrets:set DEVICE_SALT --data-file=-
   ```
   `DEVICE_SALT` tidak boleh diganti setelah rilis, karena semua kuota akan ter-reset.
3. **Param:** `PACKAGE_NAME` (default `com.snapbrain.app`). Diisi saat `firebase deploy` jika berbeda. `ADMOB_AD_UNIT_ID` (tanpa default, wajib diisi saat deploy) adalah id numerik dari rewarded ad unit. Untuk deploy non-interaktif (CI), taruh `ADMOB_AD_UNIT_ID=...` (dan `PACKAGE_NAME` jika bukan default) di `functions/.env.<projectId>`.
4. **Firestore TTL:** buat kebijakan TTL pada field `expireAt` untuk collection group `charges` dan collection `rewards`.
5. **Config:** dokumen `config/app` bersifat opsional. Field yang tersedia: `limitFree`, `limitPremium`, `rewardAmount`, `rewardMaxPerDay`, `llmModel`, `llmEffort` (`""` untuk model yang tidak mendukung effort, misalnya Haiku 4.5). Perubahan berlaku ≤ 60 detik.
6. **Play Console:** buat langganan `premium_monthly` Rp19.000/bulan. Akses Play API: aktifkan **Google Play Android Developer API** di project Google Cloud milik project Firebase. Service account runtime Functions (gen2) adalah `<PROJECT_NUMBER>-compute@developer.gserviceaccount.com`; undang ke Play Console → Users and permissions dengan izin untuk melihat data finansial dan mengelola pesanan dan langganan. Perubahan izin di Play Console bisa butuh waktu sebelum aktif; selama itu `verifyPurchase` mengembalikan `unavailable`. Buat topic Pub/Sub `play-rtdn`, beri `google-play-developer-notifications@system.gserviceaccount.com` peran Publisher, lalu isi topic itu di Monetization setup → Real-time developer notifications.
7. **AdMob:** di rewarded ad unit, aktifkan Server-side verification dengan URL fungsi `adReward`. App mengirim `device_id` sebagai `custom_data`. Callback dari ad unit lain selain `ADMOB_AD_UNIT_ID` ditolak.
8. **Deploy:** `firebase deploy --only functions,firestore:rules`.
