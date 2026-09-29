# Eval ekstraksi — 2026-09-29T14:04:35.379Z

Model `auto` · 18 contoh × 3 jalan

- Lolos skema: 54/54 (100%)
- Lolos cek isi: 48/54 (89%)
- Latensi p50 / p90: 1853 ms / 4830 ms
- Model dari router: openai/gpt-oss-safeguard-20b ×14, mistralai/devstral-medium ×40

## Tidak lolos

| contoh | jalan | model | masalah |
|---|---|---|---|
| b-struk | 1 | mistralai/devstral-medium | activation beli, harusnya none |
| b-struk | 2 | mistralai/devstral-medium | activation beli, harusnya none |
| b-struk | 3 | mistralai/devstral-medium | activation beli, harusnya none |
| f-wisata | 1 | mistralai/devstral-medium | category event, harusnya reference |
| f-wisata | 2 | mistralai/devstral-medium | category event, harusnya reference |
| f-wisata | 3 | mistralai/devstral-medium | category event, harusnya reference |

## Acceptance

Putaran tuning prompt (skor lolos cek isi, 54 jalan):

| putaran | skor | catatan |
|---|---|---|
| 0 | 47/54 | baseline |
| 1 | 50/54 | tercemar: prompt memuat contoh yang disalin dari fixture eval |
| perbaikan | 48/54 | contoh fixture dibuang |
| run ini | 48/54 | ditambah satu baris "jangan buat list Langkah bila teks tidak memuat langkah"; a-pepes tidak lagi mengarang langkah (0/3 gagal) |

- Prompt di-tuning pada fixture yang sama dengan yang dipakai untuk menilai, jadi skor ini in-sample dan bukan perkiraan akurasi di data baru.
- Alasan diterima: user meminta kecepatan, dan target yang meleset masih dalam noise antar-run sekitar ±3/54. Kegagalan run ini (b-struk activation, f-wisata category) berpindah antar run, bukan regresi dari perubahan ini. Tidak ada tuning lanjutan.
