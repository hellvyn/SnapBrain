# Checklist tes di HP (APK debug dari artifact CI `snapbrain-debug-apk`)

Artifact CI hanya terhubung ke backend asli jika repo secret `GOOGLE_SERVICES_JSON` (isi google-services.json asli) sudah diisi di GitHub → Settings → Secrets and variables → Actions. Tanpa secret itu APK dibangun dengan konfigurasi dummy dan setiap panggilan AI gagal.

Prasyarat: backend sudah di-deploy (docs/backend-ops.md). Token debug App Check sudah didaftarkan: jalankan app sekali, cari "DebugAppCheckProvider" di logcat, lalu tempel token-nya di Firebase Console → App Check → Manage debug tokens. Tanpa backend, item tampil "Menunggu internet" dan berubah menjadi "Gagal, coba lagi" setelah 5 percobaan.

1. Buka SnapBrain. Inbox kosong menampilkan "Belum ada screenshot...".
2. Galeri → pilih screenshot struk transfer → Share → SnapBrain. Sheet muncul, lalu tampil "Mengekstrak teks..." dan "AI sedang menganalisis konteks...". Hasil tampil dengan kategori 💰 Keuangan dan tombol Salin/aksi.
3. Tekan "Simpan & Hapus Asli" (Android 11+). Tombol ini hanya muncul untuk sumber MediaStore (Galeri Samsung/AOSP, tombol Share di notifikasi screenshot), tidak untuk Google Photos. Dialog sistem muncul. Setelah disetujui, foto hilang dari galeri dan item tetap ada di Inbox. Buka Detail: gambarnya tampil.
4. Share dari WhatsApp. Hanya tombol "Simpan" dan "Batal" yang muncul.
5. Mode pesawat → share screenshot. Muncul "Tersimpan. Akan diproses otomatis saat online.". Matikan mode pesawat. Dalam beberapa menit item di Inbox berubah dari "⏳ Menunggu internet" menjadi hasil AI.
6. Share lalu tutup sheet. Ditutup saat "Mengekstrak teks..." maupun "AI sedang menganalisis", item tetap tersimpan dan diproses oleh worker (hasilnya muncul di Inbox tanpa perlu membuka ulang app).
7. Share foto tanpa teks. Langsung tersimpan sebagai 📄 Lainnya, dan kuota tidak berkurang: cek di Firestore `quota/{deviceKey}.used` (Firebase Console), nilainya tidak naik.
8. Search nomor resi sebagian (misal 4 digit terakhir). Item yang cocok muncul. Chip "Belanja" memfilter kategori.
9. Detail: pinch-zoom gambar, centang task, lalu buka ulang app. Centang task tersimpan.
10. Tombol aksi: "Lacak Paket" membuka pencarian, "Tambah ke Kalender" membuka form kalender, "Buka Link" membuka browser.
11. Item berstatus "Gagal, coba lagi": di Detail tekan "Coba lagi", item masuk antrean lagi.
12. Di Detail tekan "Hapus": muncul dialog "Hapus screenshot ini?". "Batal" menutup dialog tanpa menghapus; "Hapus" menghapus item dan kembali ke Inbox.
13. Item free-tier dengan banyak tugas: Detail menampilkan 1 tugas, lalu baris terkunci dengan teks "🔒 ... tugas lain".
14. Putar layar (rotate) di Detail dan di Inbox. Teks pencarian, filter chip, dan posisi scroll tetap. Kembali dari Detail ke Inbox juga mempertahankan ketiganya.
15. Detail: setelah zoom, gambar tetap tajam dan tidak bisa digeser keluar batas. Saat tidak zoom, menggeser di area gambar tetap men-scroll halaman.
16. Mode gelap: aktifkan tema gelap di sistem, lalu cek Inbox, Detail, dialog "Hapus screenshot ini?" dan sheet Share. Teks terbaca dan tidak ada latar putih yang mencolok.
