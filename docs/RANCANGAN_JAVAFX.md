# Rancangan RestoFlow Desktop

## Keputusan yang disepakati

Pengguna memilih aplikasi executable JavaFX, anggota kelompok 3 orang, dan belum memiliki
proyek Supabase. Keputusan ini menggantikan bagian Vaadin, Spring Boot, server-side browser,
responsive mobile, serta deployment web pada JSON awal. Kebutuhan bisnis dan preservasi
data SQL tetap menjadi acuan. Kedua file sumber asli tidak diubah.

Java 17 + JavaFX 17 + Maven dipakai untuk fondasi. Spring Boot tidak dibutuhkan untuk
desktop ini. JavaFX memiliki UI native desktop; jpackage menghasilkan launcher Windows
dan runtime. Pembaruan dependency dilakukan setelah memeriksa kompatibilitas JDK.

## Tujuan dan pengguna

Membantu petugas restoran mencatat pesanan dan pembayaran secara konsisten, menghindari
kesalahan total, dan menyampaikan antrean ke dapur. Pengguna awal: admin, kasir, dan dapur.
Peran pelayan/kurir menyusul saat modul terkait dibangun. Supabase menjadi sumber data
bersama agar tiga komputer dapat melihat data yang sama; operasi bisnis memerlukan internet.
Mode offline dengan sinkronisasi belum masuk cakupan.

## Cakupan bertahap

| Tahap | Hasil | Kriteria selesai |
|---|---|---|
| 0 Fondasi | Layar persiapan dan paket Windows | Build, tes konfigurasi, startup GUI |
| 1 Data dan akses | Migrasi SQL, Auth, profil dan RLS | Jumlah data cocok; akses diizinkan/ditolak teruji |
| 2 Menu dan meja | Daftar, pencarian, edit menu, status meja | Data tersimpan dan tampil kembali setelah restart |
| 3 POS dan dapur | Keranjang, pembuatan pesanan, antrean | Transaksi atomik; gagal simpan tidak meninggalkan data parsial |
| 4 Pembayaran dan nota | Cash/Debit/QRIS manual dan cetak | Total benar; pembayaran duplikat ditolak |
| 5 Pelengkap | Dashboard, staf, delivery, ulasan, laporan | Metrik dari data; penugasan dan akses sesuai peran |
| 6 Submission | ZIP aplikasi, source, makalah, PPT | Anggota lain menjalankan paket dan mendemokan alur utama |

Tahap 0 saat ini belum membuktikan modul bisnis berjalan. Tidak ada data palsu untuk
menyimulasikan keberhasilan koneksi atau transaksi.

## Arsitektur target

```text
JavaFX View → Service (aturan bisnis) → Repository → HTTPS Supabase
                                                  ├─ Auth
                                                  ├─ PostgREST + RLS
                                                  └─ RPC transaksi PostgreSQL
```

JavaFX adalah client yang didistribusikan, sehingga rahasia administrator tidak boleh
disimpan di dalamnya. Validasi GUI membantu pengguna, tetapi validasi jumlah, harga,
otorisasi dan transisi status tetap diperiksa di database. Panggilan jaringan memakai
Task/Service JavaFX agar jendela tidak membeku. Refresh awal secara manual; Realtime menyusul
hanya bila diperlukan. Sesi login awal disimpan di memori dan dibuang saat logout.

Pembuatan pesanan menggunakan satu RPC transaksional: validasi pengguna dan menu,
baca harga dari database, buat pembayaran, pesanan, detail dan penugasan, lalu kembalikan
ID yang dihasilkan. Tidak ada dependensi FK melingkar pada SQL asli. Idempotency key
menangani klik ganda/retry. Beberapa HTTP request terpisah bukan satu transaksi database.

## Struktur target dan materi PBO

```text
src/main/java/id/ac/restoflow/
  Launcher.java, RestoFlowApp.java
  config/        konfigurasi
  domain/        MenuItem, RestaurantTable, Order, OrderItem, Payment, Staff
  service/       total, status, aturan pembayaran
  repository/    kontrak dan implementasi Supabase
  integration/   HTTP, Auth dan sesi
  ui/            tampilan per fitur
src/main/resources/styles/
src/test/java/
scripts/
docs/
```

Folder domain/service/repository/integration/ui dibuat saat fitur membutuhkannya.
Enkapsulasi: perubahan Order dikendalikan metode. Komposisi: Order memiliki OrderItem.
Abstraksi: kontrak repository. Pewarisan Staff hanya untuk KitchenStaff/Courier yang memang
memiliki relasi is-a; Waiter tetap terpisah mengikuti SQL. Strategi pembayaran digunakan
jika sudah ada perilaku berbeda, tanpa menambah interface kosong hanya demi pola desain.

## Audit dan keputusan database

- 12 tabel, 715 baris: customer/menu/staff/ulasan/pelayan/pembayaran/pemesanan/
  detail_pemesanan/diproses_oleh/nota masing-masing 65; staff_kitchen 35; kurir 30.
- Statement CREATE DATABASE awal tidak dapat langsung dipakai di PostgreSQL.
- Impor harus sekali dan mempertahankan ID. Referensi seed mengasumsikan database awal kosong.
- Terdapat 10 selisih pembayaran vs harga menu × kuantitas (pesanan 1–10).
  Total seluruh pembayaran 5.484.000, total detail dengan harga sumber 5.274.000;
  ini bukan metrik pendapatan. Total pembayaran Lunas 4.293.000.
- Tambahkan snapshot harga untuk transaksi baru. Harga historis tidak boleh dikarang.
- Arti `pemesanan.jumlah` belum dipastikan: semua 65 baris sama dengan jumlah_item.
  Jangan langsung menganggap jumlah tamu. Field guest_count baru dapat dibuat terpisah.
- Takeaway/delivery tidak boleh membutuhkan meja fiktif. Rencana migrasi: izinkan id_customer
  NULL untuk pesanan non-dine-in, tambah jenis_pesanan dan constraint yang sesuai; data lama
  tetap disimpan. Aturan inferensi lama harus terdokumentasi dan ambigu memakai unknown.
- Tambahkan profiles/Auth, pengiriman, kategori, is_active untuk arsip, serta constraint status,
  harga dan kuantitas. Aturan nota/antrian dan pengaturan restoran perlu penyimpanan eksplisit.
- Jangan otomatis menghapus staf/menu yang sudah dirujuk riwayat. `pelayan` belum dipetakan ke staff.
- Tetapkan tafsir timestamp lama sebelum konversi: SQL tidak menyimpan zona waktu.

## Instruksi pengerjaan terbaru

Fokus pada penyelesaian aplikasi sampai dapat dipakai. Tidak ada pembagian pekerjaan
atau pembuatan makalah/PPT pada lingkup aktif. Lanjutkan antartahap tanpa meminta
persetujuan ulang untuk pekerjaan aplikasi yang sudah diminta. Langkah yang bergantung
pada proyek Supabase pengguna dilaporkan terpisah dari hasil verifikasi lokal.

## Sumber teknis

- JavaFX/Maven: https://openjfx.io/openjfx-docs/
- jpackage: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
- Key client vs administrator: https://supabase.com/docs/guides/getting-started/api-keys
- RLS: https://supabase.com/docs/guides/database/postgres/row-level-security
