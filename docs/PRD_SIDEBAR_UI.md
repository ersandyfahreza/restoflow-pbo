# Product Requirements Document (PRD): Redesain Sidebar Navigasi & Penataan Tombol Kiri (JavaFX)

**Proyek:** RestoFlow Desktop (Java 17 & JavaFX 17)  
**Target Modul:** [Workspace.java](file:///d:/Belajar%20Kode/restoflow-pbo/src/main/java/id/ac/restoflow/ui/Workspace.java), [Ui.java](file:///d:/Belajar%20Kode/restoflow-pbo/src/main/java/id/ac/restoflow/ui/Ui.java), [app.css](file:///d:/Belajar%20Kode/restoflow-pbo/src/main/resources/styles/app.css)  
**Referensi Visual:** Layout Sidebar Modern (gaya *Samsa Dashboard* — hierarki bersih, ikon di kiri label, indikator aksen vertikal di sisi kiri tombol aktif, dan pemisahan grup zona atas-tengah-bawah).

---

## 1. Latar Belakang & Masalah Saat Ini (Problem Statement)

Berdasarkan implementasi saat ini pada [Workspace.java](file:///d:/Belajar%20Kode/restoflow-pbo/src/main/java/id/ac/restoflow/ui/Workspace.java#L90-L129) dan [app.css](file:///d:/Belajar%20Kode/restoflow-pbo/src/main/resources/styles/app.css#L22-L37):
1. **Navigasi Tanpa Ikon Visual:** 12 tombol navigasi (`Dashboard`, `POS`, `Pesanan`, `Dapur`, `Menu`, `Meja`, `Staf`, `Pengiriman`, `Ulasan`, `Laporan`, `Pengaturan`, `Akun`) hanya berupa teks polos sehingga pengguna memerlukan waktu lebih lama untuk memindai menu (*visual scanning*).
2. **Kepadatan Elemen di Atas Sidebar:** Bagian atas sidebar langsung menumpuk Judul Brand, Label Rentang Data, 2 `DatePicker` vertikal (`Dari` dan `Sampai`), 3 tombol preset (`Hari ini`, `7 Hari`, `30 Hari`), tombol `Terapkan rentang`, dan `CheckBox` `Auto-refresh (15d)` tanpa kartu/kontainer pengelompokan yang rapi, sehingga mendorong tombol menu navigasi terlalu jauh ke bawah.
3. **Kurangnya Pengelompokan Tombol Menu:** Seluruh menu ditampilkan dalam satu daftar datar (*flat list*) tanpa jarak atau pengelompokan kategori operasional (Transaksi Utama vs. Manajemen Data vs. Laporan & Sistem).
4. **Indikator Aktif & Footer Kurang Terstruktur:** Tombol aktif saat ini hanya mengubah seluruh latar tombol menjadi blok penuh, dan informasi status di bagian bawah (`pesanan dimuat`, `Diperbarui`, `Peran`, serta tombol `Keluar`) belum ditata dalam panel status/footer yang terorganisir seperti pada referensi UI modern.

---

## 2. Tujuan Desain (Goals & Objectives)

1. **Meningkatkan Keterbacaan (*Readability* & *Scannability*):** Menambahkan ikon vektor (`SVGPath` bawaan JavaFX tanpa perlu menambah library eksternal berat) di sebelah kiri setiap tombol navigasi dan kontrol aksi.
2. **Hierarki Zona Sidebar yang Jelas (4 Zona):** Mengadopsi tata letak referensi (*Samsa style*) yang disesuaikan dengan kebutuhan operasional RestoFlow:
   - **Zona 1 — Header Brand & Badge Status/Peran:** Logo ikon + nama `RestoFlow` serta *pill badge* status aktif (menampilkan peran pengguna & indikator `Auto-refresh` / sinkronisasi aktif, mirip badge *"Trading Active"* pada referensi).
   - **Zona 2 — Panel Filter Rentang Data yang Ringkas:** Mengemas kontrol rentang tanggal (`Dari` – `Sampai`), tombol preset (`Hari ini`, `7H`, `30H`), dan tombol `Terapkan rentang` ke dalam kartu/panel kompak yang tertata rapi.
   - **Zona 3 — Daftar Navigasi Berikon dengan Indikator Bilah Kiri:** Menu dikelompokkan secara logis, dilengkapi ikon `SVGPath` 16×16 px yang sejajar presisi (`graphicTextGap: 12px`), serta aksen garis vertikal di tepi kiri (`left accent bar`) saat menu aktif.
   - **Zona 4 — Footer Status & Aksi Keluar:** Kartu/panel ringkas di bagian bawah untuk progres data (`X / Y pesanan dimuat`), waktu pembaruan WIB, dan tombol `Keluar` berikon yang tertata tegas.
3. **Menjaga Kompatibilitas Pengujian (`WorkspaceTest`):** Seluruh `id` komponen (`#sidebar-autorefresh`, `#workspace-toast`), style class (`.sidebar`, `.nav-button`, `.active`, `.sidebar-action`, `.preset-button`, `.logout-button`), dan teks tombol (`button.getText()`) tetap dipertahankan 100% agar pengujian otomatis JUnit tidak rusak.

---

## 3. Spesifikasi Fungsional & Anatomi UI Bagian Kiri

### 3.1. Zona 1: Header Brand & Badge Peran Pengguna
- **Brand Header:** `HBox` dengan ikon aplikasi (vektor lambang restoran/sajian dalam kotak aksen `#23584B`) + teks `RestoFlow` (`Rubik Bold`, `1.45em`).
- **Status & Role Pill Badge:** Mengambil inspirasi dari *pill badge* hijau `"● Trading Active"` pada gambar referensi:
  - Menampilkan titik indikator hijau + nama tampilan / peran aktif (misal: `● Peran: Admin` atau `● Kasir Aktif`).
  - Bentuk kapsul (`-fx-background-radius: 999; -fx-padding: 4 12;`) dengan latar hijau lembut (`#DFEEE5`) dan teks hijau tua (`#173D2C`).

### 3.2. Zona 2: Panel Kontrol Rentang Data (Compact Filter Box)
Agar tidak memakan terlalu banyak ruang vertikal dan terlihat rapi:
- Dibungkus dalam kontainer `.sidebar-filter-card` (latar putih/terang `#EEF2EF`, border halus `#CBD4CE`, radius `6px`, padding `10px`).
- **Header Filter:** Label `"Rentang Data (WIB)"` dengan ikon kalender kecil.
- **Input Tanggal (`Dari` & `Sampai`):** Label `Dari` dan `Sampai` ditempatkan rapi di atas atau sejajar dengan `DatePicker` dengan tinggi proporsional.
- **Segmented Preset Buttons:** Tombol `Hari ini`, `7 Hari`, `30 Hari` disusun dalam satu baris `HBox` rata (`Hgrow.ALWAYS`) dengan gaya *segmented pill/button* yang seragam.
- **Tombol Utama Filter:** Tombol `Terapkan rentang` (`.sidebar-action`) dilengkapi ikon *refresh/check* di sebelah kiri teks, lebar penuh (`maxWidth: Double.MAX_VALUE`).
- **Toggle Auto-Refresh:** `CheckBox` `Auto-refresh (15d)` (`#sidebar-autorefresh`) ditempatkan rapi di bagian bawah kartu filter.

### 3.3. Zona 3: Daftar Menu Navigasi & Pemetaan Ikon (`SVGPath`)
Setiap tombol `.nav-button` menggunakan `ContentDisplay.LEFT`, `alignment: CENTER_LEFT`, dan `graphicTextGap: 12`.

| Grup | Nama Menu (`getText()`) | Hak Akses Peran | Ikon Vektor (`SVGPath` 16×16px) |
|---|---|---|---|
| **Utama & Transaksi** | `Dashboard` | `admin`, `cashier` | Ikon Grid 4-Kotak (*Dashboard Grid* seperti referensi) |
| | `POS` | `admin`, `cashier`, `waiter` | Ikon Mesin Kasir / Keranjang Transaksi |
| | `Pesanan` | `admin`, `cashier`, `waiter` | Ikon Daftar Nota / Clipboard Pesanan |
| | `Dapur` | `kitchen` | Ikon Api / Topi Chef / Antrean Masak |
| **Operasional & Katalog** | `Menu` | `admin`, `cashier`, `waiter` | Ikon Buku Menu / Alat Makan (Sendok & Garpu) |
| | `Meja` | `admin`, `cashier`, `waiter` | Ikon Tata Letak Meja / Kursi |
| | `Pengiriman` | `admin`, `cashier`, `courier` | Ikon Kurir / Truk Pengiriman |
| **Manajemen & Analitik** | `Staf` | `admin`, `cashier` | Ikon Grup Pengguna (*Users*) |
| | `Ulasan` | `admin`, `cashier`, `courier` | Ikon Bintang / Balon Ulasan |
| | `Laporan` | `admin`, `cashier` | Ikon Grafik Garis / Batang (*Analytics*) |
| **Sistem** | `Pengaturan` | `admin` | Ikon Roda Gigi (*Gear*) |
| | `Akun` | Semua peran | Ikon Profil Pengguna (*User Circle*) |

**Penataan Visual Tombol Navigasi (Mengacu Gambar Referensi):**
- **Keadaan Normal:** Latar transparan, teks `#30483B`, warna ikon `#526159`, padding `9px 14px`.
- **Keadaan Hover:** Latar `#DCE4DF`, teks & ikon menjadi lebih tegas (`#173D2C`), radius sudut `6px`.
- **Keadaan Aktif (`.nav-button.active`):**
  - Memiliki **indikator garis aksen vertikal di sisi kiri** (`3.5px` warna `#23584B`, menggunakan kombinasi `-fx-border-color: transparent transparent transparent #23584b; -fx-border-width: 0 0 0 4;`) dengan latar belakang lembut atau kontras tinggi, teks `bold`, dan ikon berwarna aksen utama sehingga langsung terlihat jelas halaman mana yang sedang dibuka.
- **Pemisah Antar-Grup:** Jarak vertikal ekstra (`8px`) atau garis pemisah tipis (`.nav-group-divider`) di antara grup transaksi, operasional, dan sistem agar tombol tidak terlihat menumpuk.

### 3.4. Zona 4: Footer Status & Tombol Keluar
Mengadopsi kerapian bagian bawah pada referensi (*"14 days left in trial" + progress bar + tombol "Upgrade plan"*):
- **Kartu Status Sinkronisasi (`.sidebar-status-box`):**
  - Menampilkan teks `X / Y pesanan dimuat` beserta `ProgressBar` tipis (opsional/visual) yang menggambarkan rasio pesanan yang dimuat terhadap total pesanan.
  - Menampilkan timestamp `"Diperbarui: HH:mm:ss WIB"` dengan ukuran font sekunder yang rapi.
- **Tombol `Keluar` (`.logout-button`):**
  - Dibuat sebagai tombol penuh (`maxWidth: Double.MAX_VALUE`) yang memiliki ikon *Log Out* (panah keluar pintu), border tipis merah lembut `#D8B2B3`, latar `#F9ECEC` saat *hover*, dan teks merah `#932B2D` yang rata tengah/kiri secara proporsional.

---

## 4. Spesifikasi Teknis Implementasi (JavaFX)

1. **Tanpa Dependency Eksternal Baru:**
   - Ikon dirender menggunakan komponen native JavaFX `javafx.scene.shape.SVGPath` yang dibungkus dalam `StackPane` berukuran tetap (`18×18 px`) di dalam helper `Ui.icon(String svgPathData)`.
   - Keuntungan: Ukuran file tetap ringan, tajam pada semua level zoom (`80%` – `140%`), warna ikon (`-fx-fill` / `-fx-stroke`) otomatis mengikuti state CSS (`.nav-button:hover .nav-icon`, `.nav-button.active .nav-icon`), dan tidak melanggar aturan lisensi aset proyek.
2. **Dukungan Zoom & Aksesibilitas:**
   - Lebar sidebar disesuaikan sedikit dari `235px` menjadi `248px` agar pada zoom `140%` dan resolusi `1024×700`, ikon + teks tombol tetap terbaca utuh tanpa terpotong.
   - Menggunakan `setText(label)` dan `setGraphic(iconNode)` pada `Button` JavaFX sehingga `button.getText()` tetap mengembalikan nama halaman murni (misal `"Dashboard"`), menjaga kompatibilitas penuh dengan [WorkspaceTest.java](file:///d:/Belajar%20Kode/restoflow-pbo/src/test/java/id/ac/restoflow/ui/WorkspaceTest.java#L46).

---

## 5. Kriteria Penerimaan (Acceptance Criteria)

- [ ] **AC-1 (Kelengkapan Ikon):** Seluruh 12 tombol navigasi, tombol `Terapkan rentang`, dan tombol `Keluar` memiliki ikon vektor `SVGPath` yang selaras di sebelah kiri label.
- [ ] **AC-2 (Indikator Aktif & Hover):** Tombol navigasi yang aktif memiliki indikator aksen kiri yang jelas, kontras warna yang memenuhi standar WCAG AA (≥ 4.5:1), dan *padding* yang seragam.
- [ ] **AC-3 (Penataan Kontrol Filter & Footer):** Filter tanggal, tombol preset (`Hari ini`, `7 Hari`, `30 Hari`), status data dimuat, dan peran pengguna tertata dalam grup visual yang bersih dan tidak berantakan.
- [ ] **AC-4 (Responsivitas & Zoom):** Pada ukuran jendela `1024×700` dan `1366×900` dengan zoom `80%`, `100%`, hingga `140%`, sidebar dapat di-*scroll* dengan mulus tanpa memotong tombol penting.
- [ ] **AC-5 (Lulus Pengujian Otomatis):** Perintah `./scripts/maven.ps1 verify` lulus 100% tanpa ada regresi pada 19 pengujian JUnit maupun pengujian UI `WorkspaceTest`.
