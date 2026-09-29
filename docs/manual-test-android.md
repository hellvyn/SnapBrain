# Checklist tes di HP (APK debug dari artifact CI `snapbrain-debug-apk`)

Prasyarat: backend sudah di-deploy (docs/backend-ops.md). Token debug App Check sudah didaftarkan: jalankan app sekali, cari "DebugAppCheckProvider" di logcat, lalu tempel token-nya di Firebase Console → App Check → Manage debug tokens. Tanpa backend, item akan tersimpan sebagai "Menunggu internet".

1. Buka SnapBrain. Inbox kosong menampilkan "Belum ada screenshot...".
2. Galeri → pilih screenshot struk transfer → Share → SnapBrain. Sheet muncul, lalu tampil "Mengekstrak teks..." dan "AI sedang menganalisis konteks...". Hasil tampil dengan kategori 💰 Keuangan dan tombol Salin/aksi.
3. Tekan "Simpan & Hapus Asli" (Android 11+). Dialog sistem muncul. Setelah disetujui, foto hilang dari galeri, dan item tetap terbuka di Detail beserta gambarnya.
4. Share dari WhatsApp. Hanya tombol "Simpan" dan "Batal" yang muncul.
5. Mode pesawat → share screenshot. Muncul "Tersimpan. Akan diproses otomatis saat online.". Matikan mode pesawat. Dalam beberapa menit item di Inbox berubah dari "⏳ Menunggu internet" menjadi hasil AI.
6. Share lalu langsung tutup sheet sebelum hasil keluar. Item tetap muncul di Inbox dan akhirnya terproses.
7. Share foto tanpa teks. Langsung tersimpan sebagai 📄 Lainnya, dan kuota tidak berkurang.
8. Search nomor resi sebagian (misal 4 digit terakhir). Item yang cocok muncul. Chip "Belanja" memfilter kategori.
9. Detail: pinch-zoom gambar, centang task, lalu buka ulang app. Centang task tersimpan.
10. Tombol aksi: "Lacak Paket" membuka pencarian, "Tambah ke Kalender" membuka form kalender, "Buka Link" membuka browser.
