# Checklist tes di HP (APK debug dari artifact CI `snapbrain-debug-apk`)

Artifact CI hanya terhubung ke backend asli jika repo secret `GOOGLE_SERVICES_JSON` (isi google-services.json asli) sudah diisi di GitHub → Settings → Secrets and variables → Actions. Tanpa secret itu APK dibangun dengan konfigurasi dummy dan setiap panggilan AI gagal.

Prasyarat: backend Cloudflare sudah di-deploy dan secret Worker diisi (docs/cloudflare-ops.md langkah 1–8). Token debug App Check sudah didaftarkan: buka app sekali (force stop dulu bila sudah terbuka) dengan HP tersambung ke Android Studio, filter Logcat dengan `App Check debug token`, lalu tempel kode setelah "debug token:" di Firebase Console → App Check → Manage debug tokens. Tanpa backend, item tampil "Menunggu internet" dan berubah menjadi "Gagal, coba lagi" setelah 5 percobaan.

1. Buka SnapBrain. Inbox kosong menampilkan "Belum ada screenshot...".
2. Galeri → pilih screenshot struk transfer → Share → SnapBrain. Sheet muncul, lalu tampil "Mengekstrak teks..." dan "AI sedang menganalisis konteks...". Hasil tampil dengan kategori 💰 Keuangan dan tombol Salin/aksi.
3. Tekan "Simpan & Hapus Asli" (Android 11+). Tombol ini hanya muncul untuk sumber MediaStore (Galeri Samsung/AOSP, tombol Share di notifikasi screenshot), tidak untuk Google Photos. Dialog sistem muncul. Setelah disetujui, foto hilang dari galeri dan item tetap ada di Inbox. Buka Detail: gambarnya tampil.
4. Share dari WhatsApp. Hanya tombol "Simpan" dan "Batal" yang muncul.
5. Mode pesawat → share screenshot. Muncul "Tersimpan. Akan diproses otomatis saat online.". Matikan mode pesawat. Dalam beberapa menit item di Inbox berubah dari "⏳ Menunggu internet" menjadi hasil AI.
6. Share lalu tutup sheet. Ditutup saat "Mengekstrak teks..." maupun "AI sedang menganalisis", item tetap tersimpan dan diproses oleh worker (hasilnya muncul di Inbox tanpa perlu membuka ulang app).
7. Share foto tanpa teks. Langsung tersimpan sebagai 📄 Lainnya, dan kuota tidak berkurang: cek di Cloudflare Dashboard → D1 → `snapbrain` → Console: `SELECT used FROM quota;`, nilainya tidak naik.
8. Search nomor resi sebagian (misal 4 digit terakhir). Item yang cocok muncul. Chip "Belanja" memfilter kategori.
9. Detail: tekan "Lihat screenshot asli", lalu pinch-zoom gambar di dialog. Tutup dialog, centang item daftar, lalu buka ulang app. Centang tersimpan.
10. Tombol aksi: "Lacak paket" membuka pencarian, "Kalender" membuka form kalender, "Buka link" membuka browser, "Salin" menyalin teks, "Buka Maps" membuka peta, "Chat WA" membuka WhatsApp, "Telepon" membuka dialer, "Cari di Shopee"/"Cari di Tokopedia" membuka pencarian produk.
11. Item berstatus "Gagal, coba lagi": di Detail tekan "Coba lagi", item masuk antrean lagi.
12. Di Detail tekan "Hapus": muncul dialog "Hapus screenshot ini?". "Batal" menutup dialog tanpa menghapus; "Hapus" menghapus item dan kembali ke Inbox.
13. Putar layar (rotate) di Detail dan di Inbox. Teks pencarian, filter chip, dan posisi scroll tetap. Kembali dari Detail ke Inbox juga mempertahankan ketiganya.
14. Detail: setelah zoom di dialog, gambar tetap tajam dan tidak bisa digeser keluar batas. Saat tidak zoom, menggeser di area gambar tetap men-scroll halaman.
15. Mode gelap: aktifkan tema gelap di sistem, lalu cek Inbox, Detail, dialog "Hapus screenshot ini?" dan sheet Share. Teks terbaca dan tidak ada latar putih yang mencolok.

## Fase A (daftar pintar, tampilan baru)

Pasang APK baru **di atas** APK lama (jangan uninstall dulu) untuk menguji migrasi database.

1. **Migrasi:** app terbuka tanpa crash. Screenshot lama masih ada. Detail item lama menampilkan checklist "Tugas" lama.
2. **Splash:** buka app dari launcher. Logo, "SnapBrain", dan "made with ❤️ by hellvyn" tampil sekitar 3 detik. Tap layar untuk melewati. Tap "hellvyn" membuka hellvyn.id. Share gambar dari Galeri tidak menampilkan splash.
3. **Ikon:** ikon di launcher memakai logo baru.
4. **Inbox:**
   - kartu memakai ikon kategori, bukan gambar;
   - progres "x/y" dan tenggat terdekat tampil;
   - chip kategori dan kotak cari hilang saat scroll ke bawah dan muncul lagi saat scroll ke atas;
   - pill kuota tampil setelah satu screenshot diproses.
5. **Detail:**
   - gambar tidak tampil sampai "Lihat screenshot asli" ditekan; di dialog, gambar bisa di-zoom;
   - maksimal 3 tombol aksi, dan masing-masing bekerja (kalender, Maps, WA, telepon membuka dialer tanpa menelepon, cari di Shopee/Tokopedia, salin);
   - centang item tersimpan setelah app ditutup dan dibuka lagi;
   - daftar > 8 item terlipat dengan "Tampilkan semua";
   - chip "x mnt" membuka timer di app Jam;
   - menu kartu "Salin daftar" dan "Bagikan daftar", serta tombol Bagikan di app bar, menghasilkan teks dengan ☐/☑.
6. **Contoh wajib:**
   - resep (bahan utama dan bumbu terpisah, tanpa item dobel);
   - chat grup dengan tenggat "besok jam …" (tenggat tanggal besok);
   - produk Shopee (harga terbaca, tombol "Cari di Shopee");
   - undangan acara (Kalender + Maps).
7. **Mode gelap:** ubah tema HP ke gelap. Semua layar tetap terbaca.
