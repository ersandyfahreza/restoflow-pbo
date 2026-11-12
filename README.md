# RestoFlow Desktop 1.0.0

Aplikasi JavaFX desktop untuk manajemen restoran: login, dashboard, menu, meja, POS,
antrean dapur, pembayaran manual, nota/cetak, staf, pengiriman, ulasan, laporan CSV,
pengaturan restoran, dan hak akses pengguna. Data operasional disimpan di Supabase.

## Membuka aplikasi Windows

Buka `dist/1.0.0/RestoFlow/RestoFlow.exe`. Seluruh folder RestoFlow harus tetap bersama.
Runtime Java disertakan, sehingga penerima paket tidak perlu memasang JDK.
Paket ini aplikasi portabel, bukan installer wizard. Paket lama 0.1.0 di `dist/RestoFlow`
adalah fondasi sebelumnya; gunakan versi dalam `dist/1.0.0`.

Pada pembukaan pertama pilih **Atur koneksi database**, masukkan Project URL HTTPS dan
publishable key dari proyek Supabase, lalu masuk memakai akun yang memiliki profil.
Ikuti `docs/SUPABASE_SETUP.md` untuk membuat database dan admin pertama.
Tidak ada akun/password bawaan, data lokal palsu, atau mode offline pengganti Supabase.

## Menjalankan dari source (PowerShell biasa)

Prasyarat pengembangan: Windows x64, JDK 17 (`java`, `javac`, `jpackage`), internet untuk
mengunduh dependency pertama kali. JavaFX adalah pustaka GUI; Jackson membaca JSON API;
JUnit digunakan hanya untuk pengujian. Tidak menggunakan Spring Boot/Vaadin.

```powershell
cd "D:\Kuliah Cung\Ngotak\Project PBO"
./scripts/maven.ps1 javafx:run
```

Hasil: jendela login. Tutup jendela untuk menghentikan aplikasi. Skrip menyiapkan Maven
3.9.11 resmi dengan verifikasi SHA-512 di `.tools/`, tanpa instalasi global.
Jika PowerShell memblokir skrip, ikuti kebijakan perangkat atau gunakan Maven yang
tersedia di `.tools/apache-maven-3.9.11/bin/mvn.cmd`. Jangan mengabaikan checksum gagal.
Jika unduhan gagal, periksa koneksi/proxy dan pesan error lengkap.

## Build, tes, dan pengemasan (PowerShell)

```powershell
./scripts/maven.ps1 verify
./scripts/package-windows.ps1
```

`verify` mengompilasi kode, menjalankan tes termasuk render JavaFX dan HTTP loopback,
lalu menyiapkan JAR/dependency. Pengemasan menjalankan clean verify dan jpackage.
Hasil executable: `dist/1.0.0/RestoFlow/RestoFlow.exe`.
Jika tujuan sudah ada, pindahkan paket lama dahulu; skrip tidak menimpanya otomatis.
Jangan menjalankan JAR tunggal tanpa dependency. Kemas/bagikan seluruh folder aplikasi.

## Pemakaian utama

1. Login, lalu periksa rentang tanggal pada bagian atas (WIB, maksimal 366 hari).
2. Admin mengelola menu, kategori dan meja. Data yang memiliki riwayat diarsipkan.
3. POS: pilih dine_in/takeaway/delivery, meja atau alamat sesuai kebutuhan, isi keranjang,
   dan simpan. Penugasan dapur/pelayan boleh dikosongkan atau dipilih berpasangan.
4. Dapur mengubah Menunggu → Diproses → Siap.
5. Kasir mencatat Cash atau konfirmasi manual Debit/QRIS. Pesanan lunas dapat diselesaikan.
6. Untuk delivery, kasir menugaskan kurir; kurir mengubah Ditugaskan → Diambil → Terkirim.
7. Nota dapat dipratinjau, dicetak menggunakan printer sistem, atau disimpan TXT.
8. Laporan mendukung rentang tanggal, pencarian dan ekspor CSV; grafik harian/mingguan/bulanan.

Tekan Muat ulang untuk mengambil keadaan terbaru. Semua penyimpanan dikonfirmasi server.
Jika request timeout, hasil bisa belum diketahui: muat ulang sebelum mengulangi tindakan.
Pembuatan pesanan memiliki ID permintaan untuk retry yang sama; pembayaran dikunci server.

## Konfigurasi dan keamanan

Pengaturan koneksi publik disimpan di `%USERPROFILE%/.restoflow/connection.properties`.
Environment `SUPABASE_URL` dan `SUPABASE_PUBLISHABLE_KEY` mengesampingkan file saat startup.
`.env.example` menjelaskan nama variabel; `.env` belum dimuat otomatis.
Password, access token, dan refresh token hanya di memori. Gunakan logout setelah bekerja.
Secret/service_role key dan password database tidak boleh dimasukkan ke aplikasi desktop.
Hak akses ditegakkan oleh RLS dan RPC PostgreSQL, bukan hanya visibilitas tombol.

Admin membuat akun melalui Supabase Auth, lalu memberi profil/peran di Pengaturan.
Tidak ada pemanggilan Auth Admin API dengan secret key dari desktop.
Koneksi bisnis memerlukan internet; logout saat offline menghapus sesi lokal, tetapi
pencabutan sesi server memerlukan koneksi. Tidak ada sinkronisasi offline.

## Data sumber dan batas

`DATABASE RESTORAN.sql` dan `RestoFlow_Prompt.json` tetap utuh. Migrasi berada di
`supabase/migrations/`, dan pemeriksaan di `supabase/verify.sql`. 715 baris sumber
dipertahankan saat impor. Sepuluh total historis berbeda dari detail; aplikasi menandainya.
Harga item lama memakai pembanding harga menu terkini; transaksi baru menyimpan snapshot.

Rincian dibatasi 2.000 pesanan per rentang untuk menjaga ukuran respons. Dashboard utama
menghitung seluruh rentang di server; rincian, grafik, workload dan CSV mengikuti data yang
dimuat. UI menampilkan jumlah dimuat/total. Persempit tanggal jika mencapai batas.
Jenis pesanan lama ditandai unknown, kategori awal belum dikategorikan; tidak ada inferensi
kurir/jenis/harga historis tanpa bukti. Timestamp sumber ditafsirkan Asia/Jakarta pada
kolom tambahan; nilai timestamp asli tidak diubah.

QR meja adalah identitas yang tersimpan, bukan tautan pemesanan publik. Debit/QRIS adalah
konfirmasi manual; refund, payment gateway, GPS, stok, payroll, serta installer bertanda
 tangan digital belum tersedia. Koneksi Supabase live dan klien Java sudah diuji pada proyek yang dikonfigurasi; printer fisik
dan seluruh klik GUI masih memerlukan pemeriksaan pengguna. Lihat TESTING.md untuk bukti dan batas pemeriksaan.

## Lisensi aset

Font Poppins, Rubik, dan PT Serif disertakan dari repositori resmi Google Fonts dengan
lisensi OFL masing-masing di `src/main/resources/fonts/`. Tidak ada gambar eksternal.
JavaFX: https://openjfx.io/ — Jackson: https://github.com/FasterXML/jackson-databind —
Maven: https://maven.apache.org/ — JUnit: https://junit.org/junit5/.
Lisensi kode aplikasi belum ditetapkan pemilik. Jangan mengubah atau menghapus lisensi
pustaka/runtime. Aplikasi dikembangkan dengan bantuan AI; validasi live tetap diperlukan.
