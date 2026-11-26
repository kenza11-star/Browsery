# Mesin unduhan (perubahan kecepatan)

Masalah: unduhan 70 MB dengan 8 koneksi terasa lambat.

Penyebab yang ditemukan di kode lama:

1. `HttpURLConnection` di Android memakai OkHttp dan HTTP/2 bila server mendukung. Pada HTTP/2 semua permintaan ke satu host dimultipleks di SATU koneksi TCP, jadi 8 "koneksi paralel" tidak menambah throughput.
2. File dibagi 8 potongan sama besar. Satu koneksi lambat menahan seluruh unduhan sampai potongannya selesai (ekor lambat).
3. Tiap potongan memutus koneksi (`disconnect`) sehingga tidak ada keep-alive.
4. Jeda/batal menunggu timeout baca (sampai 20 detik).

Perubahan:

- Memakai OkHttp langsung dengan protokol HTTP/1.1 dipaksa: tiap permintaan Range memakai koneksi TCP sendiri (paralel sungguhan), dan koneksi dipakai ulang antarsegmen lewat connection pool.
- File dibagi menjadi segmen kecil (sekitar 1 sampai 8 MB, sedikitnya 4 per koneksi, maksimal 512). N pekerja mengambil segmen berikutnya dari antrean, sehingga koneksi cepat mengerjakan lebih banyak.
- Buffer baca 64 KB (sebelumnya 32 KB).
- Jeda/batal membatalkan permintaan yang sedang berjalan, tidak menunggu timeout.
- Resume lama (potongan besar) tetap kompatibel; format `chunks` di database tidak berubah.

Belum terbukti: peningkatan kecepatan nyata di HP belum diukur. Uji unit hanya memeriksa kebenaran isi file, jumlah segmen, resume, dan kecepatan jeda terhadap server lokal. Server yang membatasi kecepatan per IP atau per file tidak akan menjadi lebih cepat.
