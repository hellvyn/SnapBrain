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

## Fase B (pengingat)

Siapkan: screenshot chat grup dengan tenggat "besok" dan satu dengan jam ("hari ini jam 21:00", minimal 2 jam dari sekarang).

1. **Izin:** di Android 13+, setelah screenshot bertenggat pertama diproses, kartu "Nyalakan notifikasi" muncul di atas navigasi bawah. "Izinkan" menampilkan dialog sistem. Bila ditolak, 🔔 di Detail tampil mati; tap 🔔 membuka setelan notifikasi app. "Nanti" menyembunyikan kartu.
2. **Jadwal:** tenggat jam 21:00 → notifikasi sekitar 20:00 (boleh telat beberapa menit): judul "⏰ <teks> — jam 21:00", isi "dari: <judul>". Tenggat besok tanpa jam → notifikasi besok 08:00 "— hari ini" (dan hari ini 08:00 "— besok" bila belum lewat).
   - Cepat: ubah jam HP maju melewati waktu pengingat, lalu tunggu 1–2 menit.
3. **Selesai:** tombol "Selesai" di notifikasi mencentang item (cek di Detail) dan menutup notifikasi. Tap notifikasi membuka Detail item itu tanpa splash.
4. **Batal:** centang item bertenggat, matikan 🔔 item, atau hapus screenshot-nya sebelum waktunya → notifikasi tidak muncul.
5. **Sakelar global:** Inbox ⋮ → "Matikan semua pengingat" → tidak ada notifikasi; 🔔 di Detail tampil mati dan tap menampilkan petunjuk. "Nyalakan pengingat" menjadwalkan ulang item bertenggat yang belum dicentang.
6. **Restart HP:** pengingat yang sudah dijadwalkan tetap muncul.
7. **Pasang di atas build lama:** APK Fase A dengan item bertenggat (belum dicentang) → pasang APK ini di atasnya, buka app sekali → pengingat item itu tetap muncul pada waktunya.
8. **Konfirmasi:** share screenshot bertenggat lalu tutup sheet tanpa "Simpan" → item tetap diproses dan tampil di Inbox, tapi tidak ada notifikasi. Buka item itu sekali (atau share ulang dan tekan "Simpan") → pengingat aktif.

## Fase C (Belanja, To-do)

1. **Navigasi:** bar bawah Inbox · Belanja · To-do. Back dari Belanja/To-do kembali ke Inbox. Buka Detail dari Belanja/To-do lalu back → kembali ke tab asal.
2. **Aktivasi:**
   - resep → "Masak sekarang" → menjadi "✓ Ada di Belanja & To-do"; bahan muncul di Belanja, langkah di To-do di bawah judul resep;
   - produk/keranjang → "Mau beli" → "✓ Ada di Belanja";
   - chat tugas → "Kerjakan" → "✓ Ada di To-do";
   - tap lagi → item yang belum dicentang keluar dari Belanja/To-do;
   - tagihan tanpa daftar tugas: tombol tidak tampil.
3. **Belanja per bahan:** dua resep yang sama-sama memakai bawang merah tampil satu baris "Bawang merah" dengan dua sub-baris (jumlah tidak dijumlahkan). Satu centang mencentang keduanya. "Per asal" mengelompokkan per screenshot; tap judul membuka Detail.
4. **Belanja tombol:** "Sembunyikan yang dicentang", Bagikan (teks ☐/☑ per screenshot), "Selesai belanja" mengeluarkan item yang dicentang (di Detail tetap tercentang).
5. **Total & budget:** "N barang · Rp X" hanya dari item belum dicentang yang ada harganya, "N tanpa harga" untuk sisanya. Atur budget (menu ⋮ atau tombol "Atur budget bulanan") → tampil Budget, Terbeli, Sisa. Centang produk berharga → Terbeli naik. Incaran > Sisa → peringatan merah. Kosongkan budget → bagian budget hilang.
6. **Bandingkan:** menu ⋮ → "Bandingkan harga" → pilih 2–3 screenshot produk (pilihan ke-4 tidak bisa) → tabel Harga, Ukuran, Per satuan ("Rp …/100 ml", "/100 g", "/item", atau "–"), lalu label info lain. Back kembali ke pilihan, back lagi menutup.
7. **To-do:** grup Terlambat · Hari ini · Minggu ini · Nanti · Tanpa tenggat, langkah resep di grup judul resep. Centang dari To-do tersimpan di Detail. "Tampilkan selesai" memunculkan item tercentang. 🔔 per item bertenggat.
8. **Mode gelap:** Belanja, To-do, dialog budget, Bandingkan, dan navigasi bawah tetap terbaca.
