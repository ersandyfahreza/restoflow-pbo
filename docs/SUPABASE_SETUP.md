# Menghubungkan RestoFlow ke Supabase

Aplikasi memerlukan proyek Supabase aktif. Pengujian lokal telah memakai PostgreSQL;
login Auth dan koneksi Java telah diuji pada proyek yang dikonfigurasi. Status aktual ada di SUPABASE_STATUS.md.
Tidak ada secret key yang diminta atau ditanam dalam aplikasi desktop.

## 1. Buat proyek (browser)

Buka https://supabase.com/dashboard, buat proyek, dan tunggu sampai aktif. Periksa paket
serta biaya yang ditampilkan sebelum menyetujui. Simpan password database secara aman;
password tersebut bukan password login pengguna aplikasi. Nama menu dashboard dapat berubah.

## 2. Impor database (SQL Editor di Supabase)

Gunakan proyek baru yang belum memiliki tabel dari file restoran. Jalankan satu file
pada satu waktu dalam urutan ini, menunggu sukses sebelum lanjut:

1. `supabase/migrations/001_original_data.sql`
2. `supabase/migrations/002_desktop_schema.sql`
3. `supabase/migrations/003_access.sql`
4. `supabase/migrations/004_application_api.sql`
5. `supabase/migrations/005_private_api_hardening.sql`
6. `supabase/migrations/006_staff_accounts.sql`

Dalam paket Windows, file yang sama ada di folder `database/`.
File 001 mempertahankan seluruh data asli, menghilangkan statement CREATE DATABASE yang
invalid, dan menolak impor bila tabel sumber sudah ada. Jangan menjalankan SQL asli lagi.
Migrasi berikutnya juga dijalankan sekali, bukan ditempel berulang saat aplikasi dibuka.
Masing-masing menggunakan transaksi: kesalahan membatalkan perubahan file tersebut.

Jika 001 menolak tabel yang sudah ada, periksa target dan cadangkan data. Jangan drop tabel
secara sembarang. Jika migrasi berikutnya gagal, catat error dan status file yang telah sukses;
perbaiki penyebab sebelum mengulang file yang gagal. Setelah migrasi sukses, perubahan lanjutan
memakai file migrasi baru. Recovery pada proyek berisi data memerlukan backup/restore atau
migrasi koreksi; tidak ada skrip penghapusan otomatis.

Jalankan `supabase/verify.sql` dari source untuk memeriksa hitungan, relasi, RLS dan
rekonsiliasi. Tepat setelah impor: total baris dari 12 tabel sumber 715; 65 pesanan;
46 pembayaran Lunas; total Lunas Rp4.293.000; 10 perbedaan total historis/detail.
Jumlah akan bertambah setelah aplikasi dipakai.

## 3. Buat admin pertama (Auth dan SQL Editor)

Melalui pengelolaan pengguna pada Supabase Authentication, buat satu akun email/password
untuk admin. Pastikan akun dikonfirmasi sesuai pengaturan Auth proyek, lalu salin User UID
(UUID-nya saja). Di SQL Editor, ganti placeholder di bawah dengan UID itu:

```sql
INSERT INTO public.profiles(id,display_name,role,is_active)
VALUES ('GANTI_DENGAN_UUID_DARI_AUTH'::uuid, 'Administrator', 'admin', true);
```

Placeholder bukan UUID valid sehingga tidak dapat membuat akun secara tidak sengaja.
Akun tanpa profil aktif ditolak aplikasi. Untuk akun berikutnya, admin memakai **Tambah akun**
setelah mengaktifkan fitur di langkah 5. Pengguna Auth yang sudah ada bisa dipasangkan ke
profil melalui **Hubungkan akun yang sudah ada** di Pengaturan.

- Admin/kasir: tidak perlu identitas staf.
- Kitchen: pilih `+ Buat identitas staf dapur baru` atau hubungkan ke `staff_kitchen` aktif yang belum memiliki akun.
- Courier: pilih `+ Buat identitas kurir baru` atau hubungkan ke `staff` kurir aktif yang belum memiliki akun.
- Waiter: pilih `+ Buat identitas pelayan baru` (ID pelayan baru disarankan otomatis) atau hubungkan ke ID pelayan aktif.

Pengguna tidak dapat mengubah hak aksesnya sendiri. Admin tidak boleh menurunkan profil
sendiri melalui aplikasi; admin lain atau SQL Editor mengelola perubahan tersebut.

## 4. Konfigurasikan desktop (RestoFlow)

Ambil Project URL dan publishable key di pengaturan API/Connect proyek. Buka executable,
tekan **Atur koneksi database**, isi URL `https://...supabase.co` dan publishable key,
simpan, lalu login menggunakan akun admin yang dibuat tadi.
Legacy anon key diterima bila memiliki klaim role anon. Secret/service_role ditolak.
Jangan masukkan password database. Jangan tempel password/token/key ke chat atau repository.

Konfigurasi publik disimpan di folder `.restoflow` pengguna. Bila environment variabel
SUPABASE_URL/SUPABASE_PUBLISHABLE_KEY sudah diatur, nilainya mengesampingkan file ketika
aplikasi dibuka ulang. Mengubah Project URL tidak mengubah proyek database mana pun.

Hasil: dashboard mengambil data server. Bila muncul API belum tersedia, periksa urutan
migrasi dan tunggu schema cache PostgREST diperbarui. Bila akun belum memiliki profil,
periksa UUID, status aktif, role, serta pemetaan staf/pelayan. Bila login gagal, periksa
email/password dan konfirmasi akun pada Auth. Bila koneksi timeout, periksa jaringan dan
status proyek; periksa data sebelum mengulang penyimpanan yang hasilnya tidak diketahui.

## 5. Aktifkan tambah akun dan uji ganti kata sandi

Pada proyek yang sudah memakai migrasi 001–005, cukup jalankan `006_staff_accounts.sql` di
SQL Editor. Migrasi membuat profil dan log audit dalam transaksi Supabase Auth yang sama.
Jika validasi peran, identitas aktif atau keunikan identitas gagal, transaksi dibatalkan.

Dari root source, deploy Edge Function `provision-staff-account` ke proyek yang sama memakai
Supabase CLI yang sudah login. Ganti `REF_PROYEK_ANDA` dengan referensi proyek yang benar.

```powershell
supabase functions deploy provision-staff-account --project-ref REF_PROYEK_ANDA
```

Function memeriksa token di server dan meminta RPC memeriksa profil admin aktif. Admin
memastikan alamat email milik staf dan menetapkan kata sandi awal agar staf dapat langsung
masuk. Kata sandi dikirim ke Supabase Auth dan Auth menyimpan hash, yaitu hasil pengacakan
satu arah. Profil restoran menyimpan nama, peran, dan identitas staf. Variabel
`SUPABASE_URL` dan `SUPABASE_SERVICE_ROLE_KEY` tersedia di lingkungan Edge Function Supabase.
Desktop tetap memakai publishable key dan sesi login pengguna. [Deploy Edge Functions](https://supabase.com/docs/guides/functions/quickstart) · [Variabel server](https://supabase.com/docs/guides/functions/secrets)

Di Dashboard, aktifkan penyedia login email dan nonaktifkan pendaftaran publik jika proyek
ini khusus untuk RestoFlow. **Akun → Ganti kata sandi** memverifikasi kata sandi lama dengan
Supabase Auth dan langsung menyimpan kata sandi baru. Alur ini berjalan tanpa kode email.
Supabase juga menyediakan pengaturan **Require current password** untuk mewajibkan kata sandi
lama pada permintaan perubahan melalui API. [Keamanan kata sandi Supabase](https://supabase.com/docs/guides/auth/password-security)

Uji dengan membuat akun `kitchen` yang memiliki identitas dapur aktif. Keluar, masuk dengan
email dan kata sandi awal, lalu pastikan layar **Dapur** terbuka. Pilih **Akun → Ganti kata
sandi**. Coba sekali dengan kata sandi lama yang salah. Lalu gunakan kata sandi lama yang
benar dan periksa bahwa masuk kembali hanya berhasil dengan kata sandi baru.

Jika proyek pernah memakai Edge Function `create-staff-account` dari build sebelumnya,
paket revisi menggunakan `provision-staff-account`. Untuk akun lama yang belum memiliki
kata sandi yang diketahui staf, pengelola Supabase dapat menetapkan kata sandi awal untuk
User UID yang sama melalui pengelolaan Auth yang berizin. [API admin Supabase](https://supabase.com/docs/reference/javascript/auth-admin-updateuserbyid)

## 6. Uji alur operasional live

Buat pesanan takeaway sederhana (tidak perlu meja), pindahkan Menunggu → Diproses → Siap,
catat pembayaran Cash, selesaikan pesanan, lalu buka nota. Muat ulang dan buka ulang aplikasi
untuk memastikan data tetap tersimpan. Uji akun dapur dan kurir agar tidak dapat mengakses
pembayaran atau penugasan orang lain. Gunakan akun uji yang jelas, jangan hapus data sumber.

## Sumber resmi

- API keys: https://supabase.com/docs/guides/getting-started/api-keys
- RLS: https://supabase.com/docs/guides/database/postgres/row-level-security
- Auth sessions: https://supabase.com/docs/guides/auth/sessions
