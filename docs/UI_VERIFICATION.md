# Verifikasi UI 1.0.3

Perbaikan akses pembayaran: Pesanan kini memiliki Catat pembayaran, terpisah
dari Edit isi pesanan. Tombol bayar aktif untuk Pending yang tidak dibatalkan,
termasuk Diproses/Siap; edit isi hanya Menunggu dan Pending, mengikuti aturan SQL.
GUI test membuka dialog pembayaran dari Pesanan dan Pembayaran untuk fixture
Diproses/Pending, menguji nominal awal, QRIS mengunci nominal dan Cash membukanya,
kemudian membatalkan dialog. Lunas dan edit isi pada Diproses tetap disabled.
R-26/R-35 PASS: kedua jalur tombol dan interaksi dialog teruji; tidak menulis pembayaran
live untuk pengujian. R-23 PASS: tetap memakai modul pembayaran yang sudah ada.
Versi tetap 1.0.3; tidak mengubah aturan pembayaran di database.

Tambahan 1.0.3: tombol perubahan status mengikuti transisi SQL 004, peran,
pembayaran dan pengiriman; alasan tampil pada tooltip tombol. Bila server menolak
karena data berubah, daftar dimuat ulang. Category picker Makanan/Minuman tersedia
di editor, Menu dan POS; kategori lama tetap dipertahankan, tanpa penulisan massal.
Filter kategori membandingkan field category secara tepat, tetap dikombinasikan
dengan pencarian. Tes bisnis mencakup status berulang/lompatan, pembayaran,
peran dan pengiriman; GUI memeriksa tombol disabled dan kombinasi kategori/pencarian.
19 tes JUnit; transaksi live dan pengubahan kategori menu lama tidak dilakukan.
Gate di bawah tetap berlaku pada perubahan ini: R-26/R-35 PASS didukung tes
status dan filter; R-38 PASS karena tidak mengarang klasifikasi menu lama.

13 November 2026. Cakupan perubahan: bingkai jendela Windows, login, sidebar,
hierarki dashboard, tabel, dan layout/kontrol POS. Anti-slop mode During dipilih
pengguna. Arah desain dicatat sebelum implementasi di DESIGN.md.

## Bukti pengujian

`./scripts/maven.ps1 verify`: 19 pengujian JUnit. Skenario WorkspaceTest membuka
Stage JavaFX sungguhan dengan fixture khusus tes; semua aksi GUI di bawah melalui
JavaFX Button.fire/event/control API, bukan klik manual manusia. Tidak menulis ke
Supabase. Tes menolak penulisan repository saat navigasi. Pengujian tombol simpan
POS tersendiri membuktikan callback UI, bukan keberhasilan transaksi live.

| Elemen | Aksi dan hasil yang diperiksa |
|---|---|
| 14 navigasi | Button.fire setiap tujuan; konten tampil, tidak ada pagination |
| Zoom | Ctrl +/−/0; batas 80–140%, font ikut membesar |
| Minimize | Tombol dijalankan, Windows melaporkan iconified; kemudian dipulihkan |
| Maximize/restore | Tombol dua kali, state Windows dan nama aksesibilitas berubah |
| Tutup | Ditolak oleh guard lalu diterima; Stage tetap terbuka lalu tertutup |
| Drag/resize | Event pointer menggeser posisi dan mengubah lebar sesuai delta |
| Klik ganda judul | Mengubah state menjadi maximized |
| Ctrl+F11 / Alt+F4 | Memulihkan ukuran / melewati guard penutupan |
| Loading | Konten disable, status terlihat, tombol jendela tetap enabled |
| Login kosong | Validasi terlihat; field kosong diminta fokus |
| Login terisi | Callback menerima email trim dan password; password segera dikosongkan |
| Koneksi login | Tombol memanggil callback pengaturan |
| Cari menu | Kata yang tidak cocok menghasilkan daftar kosong dan tambah disabled |
| Kategori | Makanan menyaring ke 1 menu; semua kategori mengembalikan 2 menu fixture |
| Jumlah | Spinner bertambah; keranjang menerima jumlah 2 |
| Tambah | Disabled sebelum memilih menu; memilih dan menambah menghasilkan item |
| +1 / −1 / hapus | Kuantitas 1 → 2 → 1 lalu keranjang kosong; guard mengikuti keadaan |
| Jenis pesanan | Delivery mengaktifkan alamat dan menonaktifkan meja |
| Simpan | Disabled saat kosong; callback terpanggil saat terisi |
| Pesanan baru | Batal mempertahankan keranjang; setuju mengosongkan item/alamat/jenis |
| Detail pesanan | Expanded dapat ditutup dan dibuka kembali |
| Tabel tanpa data | Placeholder membedakan sumber kosong dan pencarian kosong |

Screenshot `target/ui/`: Dashboard.png, POS.png, Pengiriman.png, Login.png,
POS-1024-80/100/140.png, POS-1366-80/100/140.png. Layout root diberi ukuran tepat
karena resize Stage pada Windows bersifat asynchronous. Batas tombol simpan
diperiksa otomatis pada keenam kombinasi ukuran/zoom. Screenshot diperiksa secara
visual; contoh 1024 × 700 dengan zoom 140% memakai scroll untuk rincian dan sidebar.

## Delivery Gate anti-slop

Status berikut berlaku pada perubahan UI ini, bukan sertifikasi seluruh fitur bisnis.
Desktop minimum 1000 × 640, tema terang; ketentuan HTML/mobile dipetakan ke JavaFX.

- R-02 PASS: tidak ada em dash pada teks UI yang diubah; nilai kosong memakai keterangan.
- R-03 PASS: enam kombinasi ukuran/zoom diuji, footer tetap dalam batas, rincian dapat discroll.
- R-17 PASS: ringkasan memakai snapshot.stats server; tidak menambahkan statistik pemasaran.
- R-18 PASS: tidak ada testimonial atau avatar fiktif.
- R-23 PASS: tidak membuat logo/gambar/struktur navigasi baru; memakai nama dan tujuan yang sudah ada.
- R-24 PASS: 14 navigasi menuju konten nyata, diuji melalui tombolnya.
- R-25 PASS: teks #202923/abu #ededed 12,79:1; sekunder #526159/abu 5,58:1; sekunder/sidebar 5,21:1; putih/hijau 8,17:1; placeholder/putih 5,09:1; error/putih 8,00:1; teks pilihan 8,59:1. Kontrol disabled dikecualikan.
- R-26 PASS: kontrol baru/diubah terhubung ke handler; daftar hasil aksi ada di tabel bukti.
- R-27 PASS: placeholder kosong/pencarian, status memuat dan validasi/error tersedia.
- R-28 PASS: tidak ada FAQ pada aplikasi ini.
- R-32 PASS: memakai kontrol JavaFX yang focus-traversable, labelFor, tombol default, serta border fokus; pintasan zoom dan jendela diuji.
- R-33 PASS: perubahan Java/CSS ditulis langsung dengan apply_patch; tidak ada skrip pengganti string sumber.
- R-34 PASS: hanya tema terang yang dikirim; tidak ada toggle tema palsu.
- R-35 PASS: build dan GUI sungguhan dijalankan; aksi kontrol pada cakupan perubahan dicatat di atas. Tidak mengklaim klik bisnis live yang tidak dilakukan.
- R-36 PASS: tidak menambahkan klaim keamanan/performa; batas tes disebutkan secara eksplisit.
- R-37 PASS: DESIGN.md menetapkan identitas, alasan visual dan dials sebelum pengerjaan.
- R-38 PASS: produksi tetap hanya Supabase; data contoh hanya fixture src/test yang diberi nama uji.
- R-39 PASS: native Button/ComboBox/Spinner, labelFor dan accessibleText; hover/armed/disabled/fokus; tidak ada animasi berulang; zoom tetap tersedia.
- R-40 PASS: tidak menambahkan media, skrip pihak ketiga atau klaim metrik web; font dibundel lokal.
- R-01 PASS: palet netral/hijau berasal dari arah desain; tidak memakai gradient/glow.
- R-04 PASS: hanya simbol jendela konvensional dengan tooltip/nama aksesibel.
- R-06 PASS: Rubik untuk judul dan Poppins untuk kontrol dijelaskan di DESIGN.md; tidak memakai monospace besar.
- R-07 PASS: tidak ada pola/grid latar dekoratif.
- R-08 PASS: tidak ada panah dekoratif pada tombol; indikator disclosure adalah kontrol native.
- R-09 PASS: tidak ada pill pemasaran atau badge di atas judul.
- R-10 PASS: tidak memakai glassmorphism.
- R-12 PASS: tidak ada bayangan komponen; hierarki memakai bidang dan batas.
- R-13 PASS: tidak ada glow pada komponen.
- R-14 PASS: ringkasan berupa satu pita data, bukan kartu fitur berulang.
- R-19 PASS: MOTION 1; tidak menambahkan animasi, hover/fokus menunjukkan interaksi.
- R-22 PASS: tidak memakai ilustrasi generik.
- Liveliness dials PASS: ENERGY 1 / RHYTHM 2 / MOTION 1 tertulis di DESIGN.md.
- Liveliness consistency PASS: operasi tenang, login/antrean/POS memakai komposisi sesuai tugas.
- Liveliness focal point PASS: form masuk, antrean dashboard, katalog dan total POS punya hierarki jelas.
- Liveliness whitespace PASS: gap memisahkan navigasi, input dan total tetap; bukti screenshot.
- Liveliness accent PASS: hijau hutan menandai aksi utama/tujuan aktif; netral untuk bidang lain.
- Liveliness identity PASS: judul Rubik dan bidang transaksi menyerupai lembar kerja kasir di seluruh layar.
- Liveliness design read PASS: arah dan alasan ditetapkan dalam DESIGN.md sebelum kode.
- C-1 PASS: alasan warna, font, tata letak dan spacing tertulis di DESIGN.md.
- C-2 PASS: handler dan hasil kontrol pada cakupan perubahan diuji dan dicatat.
- C-3 PASS: dashboard memprioritaskan antrean, POS memisahkan pemilihan dari ringkasan transaksi.
- C-4 PASS: loading/empty/validation dan ukuran/zoom diuji; scroll menjaga jangkauan konten.
- C-5 PASS: tidak ada angka atau klaim produk baru tanpa sumber.
- R-05 PASS: login dua bidang, antrean berupa tabel, POS dua area transaksi; mengikuti RHYTHM 2.
- R-11 PASS: radius kecil 4/6, baris dan bingkai siku; bukan pill di semua elemen.
- R-15 PASS: label aksi spesifik: Masuk, Tambah ke keranjang, Simpan pesanan.
- R-16 PASS: copy menjelaskan operasi restoran, tanpa buzzword pemasaran.
- R-20 PASS: motif lembar transaksi, rupiah, antrean dan footer pesanan mengikuti kebutuhan restoran.
- R-21 PASS: tema terang dipilih untuk pembacaan tabel saat bekerja, alasannya tertulis.
- R-29 PASS: netral abu/putih dan satu aksen hijau; merah hanya untuk error/tutup.
- R-30 PASS: tidak menyalin template produk lain atau menambah struktur landing page.
- R-31 PASS: keputusan visual utama memiliki alasan satu baris di DESIGN.md.

## Batas yang belum diuji ulang

Tidak melakukan transaksi baru di Supabase live pada perubahan ini. Pengujian
database/klien sebelumnya tetap tercatat sebagai hasil sebelumnya di TESTING.md,
bukan diklaim sebagai pengujian baru. Seluruh dialog CRUD, cetak fisik, perpindahan
antar-monitor/DPI, Windows Snap dan penggunaan keyboard saja sepanjang semua layar
belum diuji end-to-end. Tidak ada dependency atau migrasi database baru.
