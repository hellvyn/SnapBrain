# Eval ekstraksi — 2026-09-29T13:34:08.560Z

Model `auto` · 18 contoh × 3 jalan

- Lolos skema: 54/54 (100%)
- Lolos cek isi: 48/54 (89%)
- Latensi p50 / p90: 1773 ms / 5318 ms
- Model dari router: openai/gpt-oss-safeguard-20b ×18, mistralai/devstral-medium ×35, nemotron-3-super ×1

## Tidak lolos

| contoh | jalan | model | masalah |
|---|---|---|---|
| b-struk | 1 | nemotron-3-super | activation beli, harusnya none |
| a-pepes | 1 | mistralai/devstral-medium | langkah dikarang padahal terpotong |
| a-pepes | 2 | mistralai/devstral-medium | langkah dikarang padahal terpotong |
| a-pepes | 3 | mistralai/devstral-medium | langkah dikarang padahal terpotong |
| a-nasgor | 3 | openai/gpt-oss-safeguard-20b | bahan < 5 |
| f-materi | 1 | openai/gpt-oss-safeguard-20b | category event, harusnya reference/task |
