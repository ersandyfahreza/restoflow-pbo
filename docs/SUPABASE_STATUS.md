# Status konfigurasi Supabase

Tanggal verifikasi: 12 November 2026 (Asia/Jakarta).

- Proyek: ersandyfahreza's Project
- Referensi: jbfezqlwzphcwihwtvjh
- API URL: https://jbfezqlwzphcwihwtvjh.supabase.co
- Konfigurasi lokal RestoFlow tersimpan di folder .restoflow pengguna Windows.
- Environment SUPABASE_URL/SUPABASE_PUBLISHABLE_KEY tidak mengesampingkan konfigurasi.
- Akun demo yang diminta pengguna: admin@restoflow.test, profil admin aktif, email dikonfirmasi.
- Password acak dibuat lewat Auth Admin API; tidak disertakan dalam source, laporan, atau executable.
- PAT dan kunci administrator dipakai hanya dalam proses bootstrap tepercaya, tidak disimpan.

## Database

Lima migrasi sudah masuk ke riwayat migrasi Supabase:
001 original data, 002 desktop schema, 003 access, 004 application API, 005 private API hardening.
715 baris asli di 12 tabel dibandingkan nilai per nilai dan cocok dengan sumber.
Menu/pesanan masing-masing 65. Pembayaran Lunas sumber tetap Rp4.293.000.
Sepuluh perbedaan historis tidak diubah. Seluruh 16 tabel public mengaktifkan RLS.
Fungsi internal dipindahkan ke restoflow_private; API public hanya rf_snapshot dan rf_execute,
keduanya SECURITY INVOKER. Semua tindakan mutasi tetap memeriksa akun/peran di server.

## Verifikasi koneksi

- Endpoint Auth aktif dan mendukung email/password.
- Akses RPC dan tabel tanpa login ditolak HTTP 401.
- Login akun demo, pembacaan snapshot, penulisan pengaturan restoran yang sah,
  refresh token, pembacaan ulang, dan logout berhasil melalui HTTPS sebenarnya.
- Klien Java SupabaseClient/SupabaseRepository milik aplikasi berhasil autentikasi,
  membaca peran admin dan 65 menu/65 pesanan, lalu logout.
- Tidak ada pesanan, pembayaran, atau ulasan palsu ditambahkan untuk pengujian live.
- Pengujian PostgreSQL lokal setelah hardening: 66 pemeriksaan lulus.

## Advisor

Security Advisor: tidak ada temuan setelah hardening.
Health Advisor: tidak ada temuan saat inspeksi.
Performance Advisor: hanya informasi unused_index untuk indeks yang baru dibuat;
indeks dipertahankan karena database baru belum memiliki beban operasional.
[Penjelasan unused index](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index).

## Pemakaian

Tutup aplikasi yang sudah terbuka, lalu buka kembali executable versi 1.0.0.
Login dengan akun demo dan password yang disampaikan langsung kepada pengguna.
Pada komputer ini tidak perlu memasukkan ulang Project URL/key.
Jika paket dibagikan ke komputer lain, konfigurasikan koneksinya melalui layar aplikasi;
file koneksi pengguna Windows tidak ikut dimasukkan ke ZIP.

Masih perlu dicoba pengguna: seluruh klik operasional GUI, printer fisik, dan komputer lain.
Akun demo menggunakan domain .test sehingga tidak menerima email pemulihan; pengelolaan akun
atau penggantian email/password dilakukan melalui administrasi Auth yang tepercaya.
PAT yang pernah dikirim lewat chat sebaiknya dicabut pada pengaturan personal access tokens.
