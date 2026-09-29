# Eval ekstraksi — 2026-09-29T13:14:40.218Z

Model `auto` · 18 contoh × 3 jalan

- Lolos skema: 53/54 (98%)
- Lolos cek isi: 50/54 (93%)
- Latensi p50 / p90: 2240 ms / 13860 ms
- Model dari router: openai/gpt-oss-safeguard-20b ×33, mistralai/devstral-medium ×13, gpt-oss:20b ×4, nemotron-3-super ×1, ? ×1, mistralai/mistral-small-2603 ×1, agnes-2.5-flash ×1

## Tidak lolos

| contoh | jalan | model | masalah |
|---|---|---|---|
| b-transfer | 2 | gpt-oss:20b | activation bayar, harusnya none |
| a-pepes | 2 | openai/gpt-oss-safeguard-20b | bahan utama dan bumbu tidak dipisah; ayam hilang |
| a-nasgor | 1 | ? | gagal: TimeoutError |
| f-materi | 3 | openai/gpt-oss-safeguard-20b | category event, harusnya reference/task |
