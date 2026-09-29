import type { ExtractData } from "../src/schema";

export interface Fixture {
  id: string;
  ocr: string;
  check: (d: ExtractData) => string[];
}

const need = (ok: boolean, problem: string) => (ok ? [] : [problem]);
const items = (d: ExtractData) => d.lists.flatMap((l) => l.items);
const inRole = (d: ExtractData, role: string) => d.lists.filter((l) => l.role === role).flatMap((l) => l.items);
const steps = (d: ExtractData) => d.lists.filter((l) => l.kind === "steps").flatMap((l) => l.items);
const hasAction = (d: ExtractData, type: string, payload?: RegExp) => d.actions.some((a) => a.type === type && (!payload || payload.test(a.payload)));
const hasDue = (d: ExtractData, prefix: string) => items(d).some((i) => i.due.startsWith(prefix));
const cat = (d: ExtractData, ...ok: string[]) => need(ok.includes(d.category), `category ${d.category}, harusnya ${ok.join("/")}`);
const act = (d: ExtractData, a: string) => need(d.activation === a, `activation ${d.activation}, harusnya ${a}`);

export const FIXTURES: Fixture[] = [
  {
    id: "c-grup-kelas",
    ocr: "Grup Kimia B\nBu Rina: Laporan praktikum titrasi dikumpulkan besok jam 23.59 lewat Google Classroom ya\nBu Rina: Jangan lupa lampirkan data pengamatan\nAndi: siap bu",
    check: (d) => [...cat(d, "task"), ...need(inRole(d, "todo").length >= 1, "tidak ada to-do"), ...need(hasDue(d, "2026-09-30T23:59"), "tenggat besok 23:59 salah"), ...act(d, "kerjakan")],
  },
  {
    id: "c-kantor",
    ocr: "Tim Marketing\nSari: Budi tolong siapkan slide campaign Q4 sebelum Jumat ya\nSari: Dewi kirim laporan budget ke finance hari Kamis\nSari: Meeting review Jumat 2 Okt jam 10.00 di Ruang Rapat 3\nBudi: siap mbak",
    check: (d) => [
      ...cat(d, "task", "event"),
      ...need(inRole(d, "todo").length >= 2, "to-do < 2"),
      ...need(items(d).some((i) => /budi/i.test(i.text)), "penanggung jawab Budi hilang"),
      ...need(hasAction(d, "add_calendar", /^2026-10-02T10:00\|/), "kalender meeting 2 Okt 10:00 tidak ada"),
    ],
  },
  {
    id: "c-keluarga",
    ocr: "Mama\nDek besok jangan lupa jemput adik jam 3 sore di sekolah ya\nSekalian beli galon Aqua 2 ya\nOk ma",
    check: (d) => [...cat(d, "task"), ...need(items(d).length >= 2, "item < 2"), ...need(hasDue(d, "2026-09-30T15:00"), "jemput besok 15:00 salah")],
  },
  {
    id: "b-transfer",
    ocr: "Transfer Berhasil\n29 Sep 2026 14:05 WIB\nRp 500.000\nKe BUDI SANTOSO\nBCA 1234567890\nBerita: bayar arisan\nNo. Referensi 2609291405001",
    check: (d) => [...cat(d, "finance"), ...need(Object.keys(d.info).length >= 3, "info < 3"), ...need(hasAction(d, "copy_text"), "tidak ada salin"), ...act(d, "none")],
  },
  {
    id: "b-tagihan",
    ocr: "Tagihan Listrik PLN\nIDPEL 512345678901\nPeriode Okt 2026\nTotal Tagihan Rp 412.500\nJatuh tempo 20 Oktober 2026\nBayar via Virtual Account BNI 8808123456789012",
    check: (d) => [...cat(d, "finance"), ...need(hasDue(d, "2026-10-20"), "jatuh tempo 20 Okt tidak jadi tenggat"), ...need(hasAction(d, "copy_text", /8808123456789012/), "VA tidak bisa disalin"), ...act(d, "bayar")],
  },
  {
    id: "b-struk",
    ocr: "INDOMARET\nJl. Merdeka 10\n29.09.2026 19:12\nINDOMIE GRG 5x3.500 17.500\nAQUA 600ML 2x4.000 8.000\nROTI TAWAR 16.500\nTOTAL 42.000\nTUNAI 50.000\nKEMBALI 8.000",
    check: (d) => [...cat(d, "finance", "shopping"), ...need(items(d).length >= 3, "rincian < 3"), ...need(items(d).some((i) => i.price > 0), "harga rincian kosong"), ...act(d, "none")],
  },
  {
    id: "a-pepes",
    ocr: "Pepes Ayam Rica-Rica Kemangi\nChanchal Kaur\nBahan-bahan\n1 jam\n20 orang\n1 kg ayam potong 20 bagian\nBumbu rica-rica:\n15 buah cabai merah keriting\n9 butir bawang merah\n4 siung bawang putih\n1/2 ruas kunyit\n3 sdm lengkuas parut\n3 batang serai\n4 buah daun jeruk\n2 genggam kemangi\nsecukupnya Gula pasir dan garam\nDaun pisang dan lidi secukupnya untuk membungkus\nSimpan Resep\nCara Membuat",
    check: (d) => [
      ...cat(d, "reference"),
      ...need(d.lists.filter((l) => l.role === "belanja").length >= 2, "bahan utama dan bumbu tidak dipisah"),
      ...need(inRole(d, "belanja").some((i) => /ayam/i.test(i.text)), "ayam hilang"),
      ...need(inRole(d, "belanja").filter((i) => /kunyit/i.test(i.text)).length <= 1, "kunyit dobel"),
      ...need(steps(d).length === 0, "langkah dikarang padahal terpotong"),
      ...act(d, "masak"),
    ],
  },
  {
    id: "a-nasgor",
    ocr: "Nasi Goreng Kampung\n2 porsi · 20 menit\nBahan:\n2 piring nasi putih\n2 butir telur\n3 siung bawang merah\n2 siung bawang putih\n1 sdm kecap manis\nCara membuat:\n1. Haluskan bawang merah dan bawang putih.\n2. Tumis bumbu halus 2 menit sampai harum.\n3. Masukkan telur, orak-arik.\n4. Masukkan nasi dan kecap, aduk 5 menit.",
    check: (d) => [
      ...cat(d, "reference"),
      ...need(steps(d).length >= 4, "langkah < 4"),
      ...need(steps(d).some((i) => i.minutes > 0), "durasi langkah tidak jadi timer"),
      ...need(inRole(d, "belanja").length >= 5, "bahan < 5"),
      ...act(d, "masak"),
    ],
  },
  {
    id: "a-soto-terpotong",
    ocr: "Soto Ayam Lamongan\nBahan:\n1/2 ekor ayam\n2 batang serai\n3 lembar daun jeruk\nBumbu halus:\n6 siung bawang merah\n4 siung bawang putih\n3 butir kemiri",
    check: (d) => [...cat(d, "reference"), ...need(steps(d).length === 0, "langkah dikarang"), ...need(inRole(d, "belanja").length >= 5, "bahan < 5"), ...act(d, "masak")],
  },
  {
    id: "d-produk",
    ocr: "Gamis Katun Premium Busui Friendly\nRp189.000\nRp290.000 -35%\n4.9 ★ | 2,3RB Terjual\nVarian: Hitam, Navy, Mocca\nUkuran: M, L, XL\nGratis Ongkir\nToko Hijab Cantik Official Star+",
    check: (d) => [...cat(d, "shopping"), ...need(items(d).some((i) => i.price === 189000), "harga 189000 tidak terbaca"), ...need(hasAction(d, "search_product"), "tidak ada cari barang"), ...act(d, "beli")],
  },
  {
    id: "d-keranjang",
    ocr: "Keranjang (3)\nSabun Cair Lifebuoy 500 ml Rp37.000\nPopok Mamy Poko L isi 28 Rp89.000\nSusu UHT Ultra 1 L x2 Rp36.000\nTotal Harga Rp162.000\nBeli (3)",
    check: (d) => [
      ...cat(d, "shopping"),
      ...need(inRole(d, "belanja").length >= 3, "barang < 3"),
      ...need(items(d).filter((i) => i.price > 0).length >= 3, "harga barang kosong"),
      ...need(items(d).some((i) => i.size !== ""), "ukuran kemasan kosong"),
      ...act(d, "beli"),
    ],
  },
  {
    id: "d-redeem",
    ocr: "EVENT MLBB\nKode Redeem: MLBBOKT2026X\nBerlaku sampai 30 Sep 2026 23:59\nHadiah: 50 Diamond",
    check: (d) => [...need(hasAction(d, "copy_text", /MLBBOKT2026X/), "kode tidak bisa disalin"), ...need(hasDue(d, "2026-09-30"), "masa berlaku tidak jadi tenggat")],
  },
  {
    id: "e-workshop",
    ocr: "Invitation: BytePlus ID Lumina Training Workshop\nHi Everyone,\nWe are excited to invite you to the BytePlus ID Lumina Training Workshop!\nEvent Details\nDate & Time: Friday, October 2, 2026, 10:00 – 11:30\nLocation: ByteDance Office, Sinarmas Land Sudirman (or Online via Lark)\nSpeaker: Yilun Cai (BytePlus Product SA)\nHow to Join\nPlease register for the workshop here: Lumina Workshop Registration Form\nNote: Please bring your laptop. If you need to whitelist your account, please reach out to me (+6281519201166) in advance.\nWarm Regards,\nSandra Limawal",
    check: (d) => [
      ...cat(d, "event"),
      ...need(hasAction(d, "add_calendar", /^2026-10-02T10:00\|/), "kalender 2 Okt 10:00 tidak ada"),
      ...need(items(d).some((i) => /laptop/i.test(i.text)), "bawa laptop hilang"),
      ...act(d, "ikut"),
    ],
  },
  {
    id: "e-uts",
    ocr: "JADWAL UTS SEMESTER 5\nSenin 12 Okt 2026 08.00 Statistika R.301\nSelasa 13 Okt 2026 10.00 Basis Data Lab 2\nRabu 14 Okt 2026 13.00 Jaringan Komputer R.205",
    check: (d) => [
      ...cat(d, "event"),
      ...need(hasDue(d, "2026-10-12T08:00"), "ujian 12 Okt 08:00 salah"),
      ...need(hasDue(d, "2026-10-13T10:00"), "ujian 13 Okt 10:00 salah"),
      ...need(hasDue(d, "2026-10-14T13:00"), "ujian 14 Okt 13:00 salah"),
    ],
  },
  {
    id: "e-tiket",
    ocr: "E-Tiket KAI\nKode Booking: KX7Q2P\nArgo Parahyangan\nGambir → Bandung\nSabtu, 3 Okt 2026 07:05\nKereta 3 Kursi 12A\nPenumpang: Fadli",
    check: (d) => [
      ...cat(d, "event"),
      ...need(Object.values(d.info).some((v) => v.includes("KX7Q2P")) || hasAction(d, "copy_text", /KX7Q2P/), "kode booking hilang"),
      ...need(hasAction(d, "add_calendar", /^2026-10-03T07:05\|/), "kalender 3 Okt 07:05 tidak ada"),
    ],
  },
  {
    id: "f-wisata",
    ocr: "10 Tempat Wisata Bandung yang Wajib Dikunjungi\n1. Kawah Putih Ciwidey\n2. Tangkuban Perahu\n3. Farmhouse Lembang\n4. Dusun Bambu\n5. Tebing Keraton\n6. Orchid Forest Cikole\nBaca selengkapnya di travel.id/bandung",
    check: (d) => [...cat(d, "reference"), ...need(d.lists.some((l) => l.items.length >= 5), "daftar tempat < 5"), ...act(d, "none")],
  },
  {
    id: "f-tutorial",
    ocr: "Cara Mengganti Password WiFi IndiHome\n1. Buka 192.168.1.1 di browser\n2. Login dengan user admin\n3. Pilih menu Network > WLAN\n4. Ganti WPA Passphrase\n5. Klik Apply",
    check: (d) => [...cat(d, "reference", "task"), ...need(steps(d).length >= 5, "langkah < 5"), ...act(d, "coba")],
  },
  {
    id: "f-materi",
    ocr: "Pertemuan 5: Normalisasi Basis Data\n• 1NF: nilai atomik, tidak ada grup berulang\n• 2NF: tidak ada ketergantungan parsial\n• 3NF: tidak ada ketergantungan transitif\nTugas: rangkum 3 bentuk normal, kumpul Senin 5 Okt",
    check: (d) => [
      ...cat(d, "reference", "task"),
      ...need(hasDue(d, "2026-10-05"), "tugas Senin 5 Okt tidak jadi tenggat"),
      ...need(Object.keys(d.info).some((k) => /ringkasan/i.test(k)) || d.lists.length >= 1, "tidak ada ringkasan atau poin"),
    ],
  },
];
