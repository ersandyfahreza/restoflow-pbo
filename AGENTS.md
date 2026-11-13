# RestoFlow — aturan proyek

- Proyek tugas PBO, 3 anggota. Pengguna telah memilih executable desktop JavaFX.
- Baca README.md dan docs/RANCANGAN_JAVAFX.md. JSON awal adalah spesifikasi web yang
  telah disesuaikan oleh keputusan pengguna; jangan kembali ke Vaadin/Spring Boot.
- Pertahankan DATABASE RESTORAN.sql dan RestoFlow_Prompt.json sebagai sumber asli.
- Java 17, JavaFX 17, Maven. GUI dan logika ditulis Java. Target Windows x64.
- Jalankan dari root: `./scripts/maven.ps1 verify`, `./scripts/maven.ps1 javafx:run`.
  Paket: `./scripts/package-windows.ps1` → dist/1.0.1/RestoFlow. Jelaskan perintah dengan bahasa sederhana.
- Dependency/build cache ada di .tools; hasil build di target; distribusi di dist.
- Model, service, repository dan UI terpisah saat modul tersebut dibutuhkan.
- Instruksi terbaru pengguna: fokus menyelesaikan aplikasi, tanpa pembagian tim atau dokumen tugas.
  Lanjutkan antartahap tanpa meminta persetujuan ulang. Jangan mengklaim
  fitur bisnis selesai hanya karena UI terlihat. Dokumentasikan hasil dan batas tes.
- Jangan commit .env, password, token pengguna, secret/service_role key, atau build output.
- Desktop tidak boleh membawa administrator key. Integrasi menggunakan publishable key,
  Auth pengguna, RLS dan RPC untuk transaksi atomik. Pemeriksaan GUI bukan kontrol akses.
- Data bisnis hanya dari Supabase setelah integrasi. Tidak ada fallback array palsu.
- Jangan mengoreksi selisih data historis secara diam-diam. Gunakan migrasi terdokumentasi.
- Jangan deploy, push, membuat repository publik atau mengirim email tanpa instruksi pengguna.
- Jelaskan kegunaan dependency sebelum menambahnya. Jalankan tes yang sesuai setiap perubahan.
- Tetap ikuti instruksi pengguna yang lebih baru jika berbeda dari panduan ini.
