# Hasil pengujian RestoFlow 1.0.1

13 November 2026, Windows x64, Temurin JDK 17.0.19, JavaFX 17.0.19.

## Build dan Java

`./scripts/maven.ps1 verify` menghasilkan BUILD SUCCESS.
18 pengujian JUnit lulus; 0 failure, 0 error, 0 skipped:

- 6 konfigurasi: variabel kosong, placeholder, HTTPS origin, key administrator ditolak,
  persistensi konfigurasi dan precedence environment.
- 5 keranjang: total rupiah, penggabungan item, batas jumlah/harga, imutabilitas hasil,
  pengurangan sampai item terhapus.
- 2 HTTP: password login, refresh sesi pada batas expiry, bearer/API-key, logout,
  dan penyembunyian error internal. Menggunakan HTTP fixture loopback, bukan Supabase live.
- 4 bisnis: checkout wajib meja/alamat, server sebagai sumber harga, CSV aman dari formula,
  nota data lama dan penanda belum lunas.
- 1 render JavaFX: seluruh 14 layar navigasi terbentuk; gambar Dashboard/POS/Pengiriman
  disimpan di target/ui dan diperiksa secara visual. Kontrol zoom diuji pada 80–140%,
  termasuk pintasan keyboard dan pembesaran font. Footer status dan pagination tidak muncul
  di halaman operasional. Layout POS dua kolom dengan font Poppins, tabel berlebar responsif,
  dan daftar semua menu tanpa pagination diuji. Data fixture hanya berada di src/test, tidak
  menjadi fallback pada executable.

## Database PostgreSQL sungguhan

`python scripts/test_database.py`: 66 pemeriksaan lulus pada PostgreSQL portabel 17.10.
Database dibuat sementara, memakai emulasi schema Auth dan fungsi auth.uid untuk menguji
RLS. Ini bukan pengujian server Supabase Auth atau gateway PostgREST yang sebenarnya.

Cakupan: kelima migrasi, semua nilai dari 715 baris sumber pada 12 tabel, reimport ditolak,
rekonsiliasi 10 selisih historis, pendapatan sumber, CRUD administratif, akun nonaktif,
akses tanpa login/kasir/dapur/pelayan/kurir, snapshot harga, validasi status/kuantitas,
rollback transaksi parsial, pembayaran ganda, pengiriman, dan transaksi bersamaan.
Dua create identik bersamaan menghasilkan satu pesanan. Dua pembayaran bersamaan hanya
mengizinkan satu berhasil. Server tes hanya mendengarkan loopback dan dihentikan setelah tes.

Hasil rinci: target/database-test-results.json. Query inspeksi: supabase/verify.sql.

Untuk menyiapkan ulang alat tes (Python 3.12+ pada Windows, internet diperlukan):

```powershell
python scripts/prepare-test-postgres.py
python scripts/test_database.py
```

Biner developer disimpan di .tools, checksum SHA-256 diperiksa; tidak dipasang sebagai
layanan Windows dan tidak menjadi bagian executable produksi. Ini tambahan alat tes,
bukan database alternatif untuk data operasional aplikasi.

## Executable

jpackage berhasil membuat dist/1.0.1/RestoFlow/RestoFlow.exe beserta runtime Java.
Startup paket dengan --smoke-test menghasilkan UI_SMOKE_OK dan exit code 0.
JavaFX pada distribusi menggunakan module-path dan tidak mengeluarkan warning unnamed
module. Pengujian JUnit GUI berbasis classpath masih mencetak warning tersebut; tesnya lulus.
Skrip Maven memakai useManifestOnlyJar=false untuk menghindari masalah path Windows lintas drive.

## Batas verifikasi

- Konfigurasi Supabase live sudah diverifikasi: login admin, refresh, logout, baca snapshot dan
  penulisan pengaturan melalui API. Klien Java aplikasi juga berhasil login dan memuat data.
  Lihat docs/SUPABASE_STATUS.md untuk hasil inspeksi data dan advisor.
- Pengujian render tidak membuktikan setiap klik GUI telah diuji end-to-end. Aturan bisnis
  diuji pada service/HTTP/database; klik GUI penuh sampai pencetakan masih perlu dilakukan pengguna; klien Java → Supabase live sudah lulus.
- Printer fisik, printer PDF sistem, layar/keyboard berbeda dan komputer tanpa JDK terpisah
  belum diuji. Paket sudah menyertakan runtime, tetapi pengujian pada komputer lain tetap perlu.
- Executable belum ditandatangani dan tidak berbentuk installer wizard.
- Data rincian dibatasi 2.000 pesanan per rentang; CSV/grafik mengikuti batas yang dijelaskan UI.
- Pembayaran non-tunai manual; gateway, refund, GPS dan mode offline tidak tersedia.

## Verifikasi Supabase live 12 November 2026

Lima migrasi diterapkan. Semua nilai 715 baris dari 12 tabel sumber dibandingkan langsung
dan identik. RLS aktif pada 16 tabel public. Security Advisor tidak melaporkan temuan;
Health Advisor kosong. Performance Advisor hanya memberi informasi indeks baru belum digunakan.
Akun demo Auth dikonfirmasi dan profil admin aktif. Password/PAT tidak dicatat dalam laporan.
