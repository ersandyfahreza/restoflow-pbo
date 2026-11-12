BEGIN;
SET LOCAL search_path = public, pg_catalog;
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['customer','menu','staff','staff_kitchen','kurir','ulasan','pelayan','pembayaran','pemesanan','detail_pemesanan','diproses_oleh','nota'] LOOP
    IF to_regclass('public.' || t) IS NOT NULL THEN
      RAISE EXCEPTION 'RF: Tabel sumber % sudah ada. Impor ini hanya untuk proyek kosong; tidak ada data yang ditimpa.', t;
    END IF;
  END LOOP;
END $$;


CREATE TABLE IF NOT EXISTS public.customer
(
    id_customer integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    qr_meja character varying(255) NOT NULL,
    nomor_meja character varying(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.menu
(
    id_menu integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nama_menu character varying(50) NOT NULL,
    deskripsi_menu text,
    harga_satuan integer NOT NULL
);

CREATE TABLE IF NOT EXISTS public.staff
(
    id_staff integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY, 
    nama_staff character varying(50) NOT NULL,
    nama_depan character varying(25) NOT NULL,
    nama_belakang character varying(25) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.staff_kitchen
(
    id_staff integer PRIMARY KEY
        REFERENCES public.staff (id_staff)
);

CREATE TABLE IF NOT EXISTS public.kurir
(
    id_kurir integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_staff integer NOT NULL UNIQUE
        REFERENCES public.staff (id_staff)
);

CREATE TABLE IF NOT EXISTS public.ulasan
(
    id_ulasan integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_kurir integer NOT NULL
        REFERENCES public.kurir (id_kurir),
    tanggal_rating date NOT NULL,
    ulasan text,
    rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS public.pelayan
(
    id_pelayan character varying(10) PRIMARY KEY, 
    nama_depan character varying(25) NOT NULL,
    nama_belakang character varying(25) NOT NULL
);

-- tabel pembayaran
CREATE TABLE IF NOT EXISTS public.pembayaran
(
    id_pembayaran integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    total_harga bigint NOT NULL,
    pay_method character varying(20) NOT NULL,
    status_pembayaran character varying(50) NOT NULL DEFAULT 'menunggu'
);

-- tabel pemesanan
CREATE TABLE IF NOT EXISTS public.pemesanan
(
    id_pemesanan integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_customer integer NOT NULL
        REFERENCES public.customer (id_customer),
    id_pembayaran integer NOT NULL UNIQUE
        REFERENCES public.pembayaran (id_pembayaran),
    titik_pemesanan character varying(50),
    deskripsi_pesanan text,
    status_pemesanan character varying(25) NOT NULL DEFAULT 'menunggu',
    jumlah integer NOT NULL DEFAULT 1,
    tanggal_pemesanan timestamp without time zone NOT NULL
);

-- tabel detail_pemesanan (relasi pemesanan <-> menu)
CREATE TABLE IF NOT EXISTS public.detail_pemesanan
(
    id_pemesanan integer NOT NULL
        REFERENCES public.pemesanan (id_pemesanan),
    id_menu integer NOT NULL
        REFERENCES public.menu (id_menu),
    jumlah_item integer NOT NULL DEFAULT 1,
    PRIMARY KEY (id_pemesanan, id_menu)
);

-- tabel diproses_oleh (staff kitchen dan pelayan yang menangani pemesanan)
CREATE TABLE IF NOT EXISTS public.diproses_oleh
(
    id_pemesanan integer NOT NULL
        REFERENCES public.pemesanan (id_pemesanan),
    id_staff integer NOT NULL
        REFERENCES public.staff_kitchen (id_staff),
    id_pelayan character varying(10) NOT NULL
        REFERENCES public.pelayan (id_pelayan),
    PRIMARY KEY (id_pemesanan, id_staff, id_pelayan)
);

-- tabel nota
CREATE TABLE IF NOT EXISTS public.nota
(
    id_nota integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pembayaran integer NOT NULL
        REFERENCES public.pembayaran (id_pembayaran),
    nomor_antrian character varying(10) NOT NULL,   -- contoh: A01
    list_menu character varying(100) NOT NULL
);
-- Data lengkap id 1-65 untuk semua tabel

INSERT INTO customer (qr_meja, nomor_meja) VALUES
('QR-MJ-001', 'Meja 1'),
('QR-MJ-002', 'Meja 2'),
('QR-MJ-003', 'Meja 3'),
('QR-MJ-004', 'Meja 4'),
('QR-MJ-005', 'Meja 5'),
('QR-MJ-006', 'Meja 6'),
('QR-MJ-007', 'Meja 7'),
('QR-MJ-008', 'Meja 8'),
('QR-MJ-009', 'Meja 9'),
('QR-MJ-010', 'Meja 10'),
('QR-MJ-011', 'Meja 11'),
('QR-MJ-012', 'Meja 12'),
('QR-MJ-013', 'Meja 13'),
('QR-MJ-014', 'Meja 14'),
('QR-MJ-015', 'Meja 15'),
('QR-MJ-016', 'Meja 16'),
('QR-MJ-017', 'Meja 17'),
('QR-MJ-018', 'Meja 18'),
('QR-MJ-019', 'Meja 19'),
('QR-MJ-020', 'Meja 20'),
('QR-MJ-021', 'Meja 21'),
('QR-MJ-022', 'Meja 22'),
('QR-MJ-023', 'Meja 23'),
('QR-MJ-024', 'Meja 24'),
('QR-MJ-025', 'Meja 25'),
('QR-MJ-026', 'Meja 26'),
('QR-MJ-027', 'Meja 27'),
('QR-MJ-028', 'Meja 28'),
('QR-MJ-029', 'Meja 29'),
('QR-MJ-030', 'Meja 30'),
('QR-MJ-031', 'Meja 31'),
('QR-MJ-032', 'Meja 32'),
('QR-MJ-033', 'Meja 33'),
('QR-MJ-034', 'Meja 34'),
('QR-MJ-035', 'Meja 35'),
('QR-MJ-036', 'Meja 36'),
('QR-MJ-037', 'Meja 37'),
('QR-MJ-038', 'Meja 38'),
('QR-MJ-039', 'Meja 39'),
('QR-MJ-040', 'Meja 40'),
('QR-MJ-041', 'Meja 41'),
('QR-MJ-042', 'Meja 42'),
('QR-MJ-043', 'Meja 43'),
('QR-MJ-044', 'Meja 44'),
('QR-MJ-045', 'Meja 45'),
('QR-MJ-046', 'Meja 46'),
('QR-MJ-047', 'Meja 47'),
('QR-MJ-048', 'Meja 48'),
('QR-MJ-049', 'Meja 49'),
('QR-MJ-050', 'Meja 50'),
('QR-MJ-051', 'Meja 51'),
('QR-MJ-052', 'Meja 52'),
('QR-MJ-053', 'Meja 53'),
('QR-MJ-054', 'Meja 54'),
('QR-MJ-055', 'Meja 55'),
('QR-MJ-056', 'Meja 56'),
('QR-MJ-057', 'Meja 57'),
('QR-MJ-058', 'Meja 58'),
('QR-MJ-059', 'Meja 59'),
('QR-MJ-060', 'Meja 60'),
('QR-MJ-061', 'Meja 61'),
('QR-MJ-062', 'Meja 62'),
('QR-MJ-063', 'Meja 63'),
('QR-MJ-064', 'Meja 64'),
('QR-MJ-065', 'Meja 65');

INSERT INTO menu (nama_menu, deskripsi_menu, harga_satuan) VALUES
('Nasi Goreng Spesial', 'Nasi goreng dengan telur dan ayam suwir', 25000),
('Mie Ayam Bakso', 'Mie ayam dengan tambahan bakso sapi', 22000),
('Ayam Geprek Sambal Matah', NULL, 27000),
('Sate Ayam Madura', '10 tusuk sate dengan bumbu kacang', 28000),
('Soto Betawi', NULL, 30000),
('Gado-Gado Jakarta', 'Sayuran segar dengan bumbu kacang khas Betawi', 20000),
('Nasi Uduk Komplit', 'Nasi uduk dengan ayam goreng dan telur balado', 26000),
('Es Teh Manis', NULL, 6000),
('Es Jeruk Peras', 'Jeruk peras segar tanpa pengawet', 8000),
('Kopi Susu Gula Aren', 'Espresso dengan susu dan gula aren', 18000),
('Bakso Urat Jumbo', 'Bakso urat besar dengan kuah kaldu sapi', 24000),
('Bakso Beranak', 'Bakso besar berisi bakso kecil dan telur puyuh', 28000),
('Rawon Daging', 'Sup daging kuah kluwek khas Jawa Timur', 32000),
('Rendang Sapi', 'Daging sapi masak santan dan rempah', 35000),
('Gudeg Jogja Komplit', 'Gudeg nangka dengan ayam, telur, dan krecek', 27000),
('Pecel Lele', NULL, 22000),
('Pecel Ayam', NULL, 24000),
('Soto Ayam Lamongan', 'Soto ayam kuah kuning dengan koya', 23000),
('Soto Kudus', 'Soto kuah bening porsi kecil', 25000),
('Tongseng Kambing', 'Daging kambing kuah santan dan kol', 38000),
('Gulai Kambing', 'Gulai kambing dengan bumbu kari khas Padang', 40000),
('Sop Buntut', 'Buntut sapi empuk dengan kuah bening', 55000),
('Iga Bakar Madu', 'Iga sapi bakar dengan olesan madu', 58000),
('Ayam Bakar Taliwang', 'Ayam bakar pedas khas Lombok', 30000),
('Ayam Goreng Kremes', 'Ayam goreng dengan taburan kremes renyah', 26000),
('Bebek Goreng Sambal Ijo', NULL, 35000),
('Nasi Liwet Solo', 'Nasi gurih santan dengan suwiran ayam dan telur', 28000),
('Nasi Pecel Madiun', 'Nasi dengan sayuran dan bumbu pecel', 21000),
('Nasi Campur Bali', 'Nasi dengan lawar, sate lilit, dan ayam betutu', 29000),
('Nasi Kuning Tumpeng Mini', 'Nasi kuning dengan lauk lengkap', 24000),
('Lontong Sayur', NULL, 17000),
('Ketoprak', 'Lontong, tahu, bihun dengan bumbu kacang', 18000),
('Tahu Tek', NULL, 19000),
('Tahu Gejrot', 'Tahu goreng dengan kuah gula merah pedas', 14000),
('Siomay Bandung', 'Siomay ikan dengan bumbu kacang', 20000),
('Batagor', 'Bakso tahu goreng dengan saus kacang', 18000),
('Pempek Kapal Selam', 'Pempek isi telur dengan cuko', 25000),
('Mie Goreng Jawa', 'Mie goreng bumbu kecap manis dengan telur', 23000),
('Mie Kuah Nyemek', NULL, 22000),
('Kwetiau Goreng Seafood', 'Kwetiau dengan udang dan cumi', 32000),
('Capcay Goreng', 'Tumis aneka sayuran dengan saus tiram', 27000),
('Cumi Goreng Tepung', 'Cumi renyah dengan saus tartar', 34000),
('Udang Saus Padang', 'Udang dengan saus pedas manis', 42000),
('Ikan Bakar Jimbaran', 'Ikan laut bakar dengan sambal matah', 36000),
('Gurame Asam Manis', 'Ikan gurame goreng dengan saus asam manis', 60000),
('Tempe Mendoan', NULL, 10000),
('Tahu Crispy', NULL, 12000),
('Pisang Goreng Keju', 'Pisang goreng dengan taburan keju dan susu', 15000),
('Kentang Goreng', NULL, 16000),
('Roti Bakar Cokelat', 'Roti bakar dengan selai cokelat dan keju', 17000),
('Martabak Mini Manis', 'Martabak manis porsi kecil', 20000),
('Es Cendol Dawet', 'Cendol dengan santan dan gula merah', 12000),
('Es Campur', NULL, 14000),
('Es Doger', 'Es serut dengan santan, tape, dan roti', 13000),
('Es Kelapa Muda', 'Kelapa muda segar dengan sirup', 12000),
('Es Teler', 'Alpukat, nangka, kelapa, dan susu', 16000),
('Wedang Jahe', NULL, 9000),
('Teh Tarik', 'Teh susu yang ditarik hingga berbusa', 14000),
('Kopi Hitam Tubruk', NULL, 10000),
('Cappuccino', 'Espresso dengan susu berbusa', 20000),
('Latte Caramel', 'Espresso, susu, dan sirup karamel', 22000),
('Matcha Latte', 'Matcha dengan susu segar', 23000),
('Jus Alpukat', 'Alpukat dengan susu cokelat', 16000),
('Jus Mangga', NULL, 15000),
('Jus Jambu Merah', 'Jambu merah segar tanpa pengawet', 15000);

INSERT INTO staff (nama_staff, nama_depan, nama_belakang) VALUES
('Budi Santoso', 'Budi', 'Santoso'),  -- id 1 -> kitchen
('Siti Aminah', 'Siti', 'Aminah'),  -- id 2 -> kitchen
('Agus Wijaya', 'Agus', 'Wijaya'),  -- id 3 -> kitchen
('Dewi Lestari', 'Dewi', 'Lestari'),  -- id 4 -> kitchen
('Rudi Hartono', 'Rudi', 'Hartono'),  -- id 5 -> kitchen
('Fajar Nugroho', 'Fajar', 'Nugroho'),  -- id 6 -> kurir
('Maya Puspita', 'Maya', 'Puspita'),  -- id 7 -> kurir
('Rian Saputra', 'Rian', 'Saputra'),  -- id 8 -> kurir
('Indah Permata', 'Indah', 'Permata'),  -- id 9 -> kurir
('Yusuf Ramadhan', 'Yusuf', 'Ramadhan'),  -- id 10 -> kurir
('Ahmad Rahayu', 'Ahmad', 'Rahayu'),  -- id 11 -> kitchen
('Bagus Purnomo', 'Bagus', 'Purnomo'),  -- id 12 -> kitchen
('Cahyo Anwar', 'Cahyo', 'Anwar'),  -- id 13 -> kitchen
('Dimas Permadi', 'Dimas', 'Permadi'),  -- id 14 -> kitchen
('Eko Lubis', 'Eko', 'Lubis'),  -- id 15 -> kitchen
('Farida Wahyuni', 'Farida', 'Wahyuni'),  -- id 16 -> kitchen
('Galih Rohman', 'Galih', 'Rohman'),  -- id 17 -> kitchen
('Hana Hidayat', 'Hana', 'Hidayat'),  -- id 18 -> kitchen
('Irfan Handoko', 'Irfan', 'Handoko'),  -- id 19 -> kitchen
('Jihan Hermawan', 'Jihan', 'Hermawan'),  -- id 20 -> kitchen
('Kevin Yulianto', 'Kevin', 'Yulianto'),  -- id 21 -> kitchen
('Lina Octaviani', 'Lina', 'Octaviani'),  -- id 22 -> kitchen
('Mulyadi Siregar', 'Mulyadi', 'Siregar'),  -- id 23 -> kitchen
('Nadia Amalia', 'Nadia', 'Amalia'),  -- id 24 -> kitchen
('Oki Effendi', 'Oki', 'Effendi'),  -- id 25 -> kitchen
('Putra Kusuma', 'Putra', 'Kusuma'),  -- id 26 -> kitchen
('Qori Firmansyah', 'Qori', 'Firmansyah'),  -- id 27 -> kitchen
('Rina Suryani', 'Rina', 'Suryani'),  -- id 28 -> kitchen
('Satria Basuki', 'Satria', 'Basuki'),  -- id 29 -> kitchen
('Tia Hakim', 'Tia', 'Hakim'),  -- id 30 -> kitchen
('Umar Nasution', 'Umar', 'Nasution'),  -- id 31 -> kitchen
('Vina Fadillah', 'Vina', 'Fadillah'),  -- id 32 -> kitchen
('Wahyu Gumelar', 'Wahyu', 'Gumelar'),  -- id 33 -> kitchen
('Yoga Wibowo', 'Yoga', 'Wibowo'),  -- id 34 -> kitchen
('Zahra Cahyani', 'Zahra', 'Cahyani'),  -- id 35 -> kitchen
('Arif Kurnia', 'Arif', 'Kurnia'),  -- id 36 -> kitchen
('Bambang Kristanto', 'Bambang', 'Kristanto'),  -- id 37 -> kitchen
('Candra Yuliani', 'Candra', 'Yuliani'),  -- id 38 -> kitchen
('Dian Harahap', 'Dian', 'Harahap'),  -- id 39 -> kitchen
('Edi Hartati', 'Edi', 'Hartati'),  -- id 40 -> kitchen
('Fitri Iskandar', 'Fitri', 'Iskandar'),  -- id 41 -> kurir
('Gilang Setiawan', 'Gilang', 'Setiawan'),  -- id 42 -> kurir
('Hasan Wijayanti', 'Hasan', 'Wijayanti'),  -- id 43 -> kurir
('Ika Maharani', 'Ika', 'Maharani'),  -- id 44 -> kurir
('Joni Ramadani', 'Joni', 'Ramadani'),  -- id 45 -> kurir
('Kartika Fauzi', 'Kartika', 'Fauzi'),  -- id 46 -> kurir
('Lukman Simanjuntak', 'Lukman', 'Simanjuntak'),  -- id 47 -> kurir
('Mega Marlina', 'Mega', 'Marlina'),  -- id 48 -> kurir
('Nanda Julianto', 'Nanda', 'Julianto'),  -- id 49 -> kurir
('Omar Pratama', 'Omar', 'Pratama'),  -- id 50 -> kurir
('Pratiwi Saputro', 'Pratiwi', 'Saputro'),  -- id 51 -> kurir
('Rizky Susanto', 'Rizky', 'Susanto'),  -- id 52 -> kurir
('Salsa Syahputra', 'Salsa', 'Syahputra'),  -- id 53 -> kurir
('Tono Ardiansyah', 'Tono', 'Ardiansyah'),  -- id 54 -> kurir
('Usman Pangestu', 'Usman', 'Pangestu'),  -- id 55 -> kurir
('Vero Kusumawati', 'Vero', 'Kusumawati'),  -- id 56 -> kurir
('Wulan Kartono', 'Wulan', 'Kartono'),  -- id 57 -> kurir
('Yanto Utomo', 'Yanto', 'Utomo'),  -- id 58 -> kurir
('Zainal Nugraha', 'Zainal', 'Nugraha'),  -- id 59 -> kurir
('Anisa Wardana', 'Anisa', 'Wardana'),  -- id 60 -> kurir
('Bayu Mahendra', 'Bayu', 'Mahendra'),  -- id 61 -> kurir
('Cindy Budiman', 'Cindy', 'Budiman'),  -- id 62 -> kurir
('Danu Santika', 'Danu', 'Santika'),  -- id 63 -> kurir
('Elsa Sulistyo', 'Elsa', 'Sulistyo'),  -- id 64 -> kurir
('Fikri Mulia', 'Fikri', 'Mulia');  -- id 65 -> kurir

INSERT INTO staff_kitchen (id_staff) VALUES
(1),
(2),
(3),
(4),
(5),
(11),
(12),
(13),
(14),
(15),
(16),
(17),
(18),
(19),
(20),
(21),
(22),
(23),
(24),
(25),
(26),
(27),
(28),
(29),
(30),
(31),
(32),
(33),
(34),
(35),
(36),
(37),
(38),
(39),
(40);

INSERT INTO kurir (id_staff) VALUES
(6),
(7),
(8),
(9),
(10),
(41),
(42),
(43),
(44),
(45),
(46),
(47),
(48),
(49),
(50),
(51),
(52),
(53),
(54),
(55),
(56),
(57),
(58),
(59),
(60),
(61),
(62),
(63),
(64),
(65);

INSERT INTO ulasan (id_kurir, tanggal_rating, ulasan, rating) VALUES
(1, '2026-08-20', 'Pengantaran cepat dan ramah', 5),
(2, '2026-08-25', 'Sedikit terlambat tapi sopan', 3),
(3, '2026-09-01', 'Sangat memuaskan, makanan masih panas', 5),
(4, '2026-09-05', 'Biasa saja', 2),
(5, '2026-09-10', 'Pelayanan oke, pengemasan rapi', 4),
(11, '2026-09-12', 'Cukup cepat, makanan masih hangat', 3),
(12, '2026-09-13', 'Sangat cepat dan ramah sekali', 5),
(13, '2026-09-14', 'Biasa saja, tidak ada yang spesial', 2),
(14, '2026-09-15', 'Kurir sopan dan pengemasan rapi', 4),
(15, '2026-09-16', 'Kurir kurang ramah dan pesanan tumpah', 1),
(16, '2026-09-17', 'Standar, sesuai estimasi waktu', 3),
(17, '2026-09-18', 'Luar biasa, makanan masih panas dan rapi', 5),
(18, '2026-09-19', 'Pengantaran lama dan kemasan kurang rapi', 2),
(19, '2026-09-20', 'Pengantaran tepat waktu', 4),
(20, '2026-09-21', 'Pesanan telat sekali dan makanan sudah dingin', 1),
(21, '2026-09-22', 'Cukup cepat, makanan masih hangat', 3),
(22, '2026-09-23', 'Sangat cepat dan ramah sekali', 5),
(23, '2026-09-24', 'Biasa saja, tidak ada yang spesial', 2),
(24, '2026-09-25', 'Kurir sopan dan pengemasan rapi', 4),
(25, '2026-09-26', 'Kurir kurang ramah dan pesanan tumpah', 1),
(26, '2026-09-27', 'Standar, sesuai estimasi waktu', 3),
(27, '2026-09-28', 'Luar biasa, makanan masih panas dan rapi', 5),
(28, '2026-09-29', 'Pengantaran lama dan kemasan kurang rapi', 2),
(29, '2026-09-30', 'Pengantaran tepat waktu', 4),
(30, '2026-10-01', 'Pesanan telat sekali dan makanan sudah dingin', 1),
(1, '2026-10-02', 'Cukup cepat, makanan masih hangat', 3),
(2, '2026-10-03', 'Sangat cepat dan ramah sekali', 5),
(3, '2026-10-04', 'Biasa saja, tidak ada yang spesial', 2),
(4, '2026-10-05', 'Kurir sopan dan pengemasan rapi', 4),
(5, '2026-10-06', 'Kurir kurang ramah dan pesanan tumpah', 1),
(6, '2026-10-07', 'Standar, sesuai estimasi waktu', 3),
(7, '2026-10-08', 'Luar biasa, makanan masih panas dan rapi', 5),
(8, '2026-10-09', 'Pengantaran lama dan kemasan kurang rapi', 2),
(9, '2026-10-10', 'Pengantaran tepat waktu', 4),
(10, '2026-10-11', 'Pesanan telat sekali dan makanan sudah dingin', 1),
(11, '2026-10-12', 'Cukup cepat, makanan masih hangat', 3),
(12, '2026-10-13', 'Sangat cepat dan ramah sekali', 5),
(13, '2026-10-14', 'Biasa saja, tidak ada yang spesial', 2),
(14, '2026-10-15', 'Kurir sopan dan pengemasan rapi', 4),
(15, '2026-10-16', 'Kurir kurang ramah dan pesanan tumpah', 1),
(16, '2026-10-17', 'Standar, sesuai estimasi waktu', 3),
(17, '2026-10-18', 'Luar biasa, makanan masih panas dan rapi', 5),
(18, '2026-10-19', 'Pengantaran lama dan kemasan kurang rapi', 2),
(19, '2026-10-20', 'Pengantaran tepat waktu', 4),
(20, '2026-10-21', 'Pesanan telat sekali dan makanan sudah dingin', 1),
(21, '2026-10-22', 'Cukup cepat, makanan masih hangat', 3),
(22, '2026-10-23', 'Sangat cepat dan ramah sekali', 5),
(23, '2026-10-24', 'Biasa saja, tidak ada yang spesial', 2),
(24, '2026-10-25', 'Kurir sopan dan pengemasan rapi', 4),
(25, '2026-10-26', 'Kurir kurang ramah dan pesanan tumpah', 1),
(26, '2026-10-27', 'Standar, sesuai estimasi waktu', 3),
(27, '2026-10-28', 'Luar biasa, makanan masih panas dan rapi', 5),
(28, '2026-10-29', 'Pengantaran lama dan kemasan kurang rapi', 2),
(29, '2026-10-30', 'Pengantaran tepat waktu', 4),
(30, '2026-10-31', 'Pesanan telat sekali dan makanan sudah dingin', 1),
(1, '2026-11-01', 'Cukup cepat, makanan masih hangat', 3),
(2, '2026-11-02', 'Sangat cepat dan ramah sekali', 5),
(3, '2026-11-03', 'Biasa saja, tidak ada yang spesial', 2),
(4, '2026-11-04', 'Kurir sopan dan pengemasan rapi', 4),
(5, '2026-11-05', 'Kurir kurang ramah dan pesanan tumpah', 1),
(6, '2026-11-01', 'Standar, sesuai estimasi waktu', 3),
(7, '2026-11-02', 'Luar biasa, makanan masih panas dan rapi', 5),
(8, '2026-11-03', 'Pengantaran lama dan kemasan kurang rapi', 2),
(9, '2026-11-04', 'Pengantaran tepat waktu', 4),
(10, '2026-11-05', 'Pesanan telat sekali dan makanan sudah dingin', 1);

INSERT INTO pelayan (id_pelayan, nama_depan, nama_belakang) VALUES
('PLY-01', 'Andi', 'Prasetyo'),
('PLY-02', 'Bella', 'Anggraini'),
('PLY-03', 'Citra', 'Dewanti'),
('PLY-04', 'Doni', 'Kurniawan'),
('PLY-05', 'Eka', 'Putri'),
('PLY-06', 'Farhan', 'Maulana'),
('PLY-07', 'Gita', 'Sari'),
('PLY-08', 'Hendra', 'Gunawan'),
('PLY-09', 'Intan', 'Pertiwi'),
('PLY-10', 'Joko', 'Susilo'),
('PLY-11', 'Fikri', 'Purnomo'),
('PLY-12', 'Elsa', 'Kurnia'),
('PLY-13', 'Danu', 'Wardana'),
('PLY-14', 'Cindy', 'Basuki'),
('PLY-15', 'Bayu', 'Syahputra'),
('PLY-16', 'Anisa', 'Octaviani'),
('PLY-17', 'Zainal', 'Fauzi'),
('PLY-18', 'Yanto', 'Lubis'),
('PLY-19', 'Wulan', 'Harahap'),
('PLY-20', 'Vero', 'Santika'),
('PLY-21', 'Usman', 'Fadillah'),
('PLY-22', 'Tono', 'Kusumawati'),
('PLY-23', 'Salsa', 'Effendi'),
('PLY-24', 'Rizky', 'Julianto'),
('PLY-25', 'Pratiwi', 'Hidayat'),
('PLY-26', 'Omar', 'Setiawan'),
('PLY-27', 'Nanda', 'Rahayu'),
('PLY-28', 'Mega', 'Cahyani'),
('PLY-29', 'Lukman', 'Nugraha'),
('PLY-30', 'Kartika', 'Suryani'),
('PLY-31', 'Joni', 'Susanto'),
('PLY-32', 'Ika', 'Yulianto'),
('PLY-33', 'Hasan', 'Ramadani'),
('PLY-34', 'Gilang', 'Permadi'),
('PLY-35', 'Fitri', 'Yuliani'),
('PLY-36', 'Edi', 'Budiman'),
('PLY-37', 'Dian', 'Nasution'),
('PLY-38', 'Candra', 'Pangestu'),
('PLY-39', 'Bambang', 'Amalia'),
('PLY-40', 'Arif', 'Marlina'),
('PLY-41', 'Zahra', 'Rohman'),
('PLY-42', 'Yoga', 'Iskandar'),
('PLY-43', 'Wahyu', 'Mulia'),
('PLY-44', 'Vina', 'Wibowo'),
('PLY-45', 'Umar', 'Utomo'),
('PLY-46', 'Tia', 'Firmansyah'),
('PLY-47', 'Satria', 'Saputro'),
('PLY-48', 'Rina', 'Hermawan'),
('PLY-49', 'Qori', 'Maharani'),
('PLY-50', 'Putra', 'Anwar'),
('PLY-51', 'Oki', 'Kristanto'),
('PLY-52', 'Nadia', 'Mahendra'),
('PLY-53', 'Mulyadi', 'Hakim'),
('PLY-54', 'Lina', 'Ardiansyah'),
('PLY-55', 'Kevin', 'Siregar'),
('PLY-56', 'Jihan', 'Simanjuntak'),
('PLY-57', 'Irfan', 'Wahyuni'),
('PLY-58', 'Hana', 'Hartati'),
('PLY-59', 'Galih', 'Sulistyo'),
('PLY-60', 'Farida', 'Gumelar'),
('PLY-61', 'Eko', 'Kartono'),
('PLY-62', 'Dimas', 'Kusuma'),
('PLY-63', 'Cahyo', 'Pratama'),
('PLY-64', 'Bagus', 'Handoko'),
('PLY-65', 'Ahmad', 'Wijayanti');

INSERT INTO pembayaran (total_harga, pay_method, status_pembayaran) VALUES
(75000, 'QRIS', 'Lunas'),
(52000, 'Cash', 'Lunas'),
(120000, 'Debit', 'Lunas'),
(45000, 'QRIS', 'Pending'),
(89000, 'Cash', 'Lunas'),
(63000, 'QRIS', 'Lunas'),
(150000, 'Debit', 'Lunas'),
(38000, 'Cash', 'Pending'),
(97000, 'QRIS', 'Lunas'),
(56000, 'Debit', 'Gagal'),
(144000, 'Debit', 'Lunas'),
(28000, 'QRIS', 'Lunas'),
(64000, 'Cash', 'Lunas'),
(105000, 'Debit', 'Pending'),
(108000, 'QRIS', 'Lunas'),
(110000, 'Cash', 'Lunas'),
(144000, 'Debit', 'Lunas'),
(23000, 'QRIS', 'Pending'),
(50000, 'Cash', 'Lunas'),
(114000, 'Debit', 'Gagal'),
(160000, 'QRIS', 'Lunas'),
(275000, 'Cash', 'Lunas'),
(348000, 'Debit', 'Lunas'),
(30000, 'QRIS', 'Pending'),
(52000, 'Cash', 'Lunas'),
(105000, 'Debit', 'Lunas'),
(112000, 'QRIS', 'Lunas'),
(105000, 'Cash', 'Pending'),
(174000, 'Debit', 'Lunas'),
(24000, 'QRIS', 'Gagal'),
(34000, 'Cash', 'Lunas'),
(54000, 'Debit', 'Lunas'),
(76000, 'QRIS', 'Lunas'),
(70000, 'Cash', 'Pending'),
(120000, 'Debit', 'Lunas'),
(18000, 'QRIS', 'Lunas'),
(50000, 'Cash', 'Lunas'),
(69000, 'Debit', 'Pending'),
(88000, 'QRIS', 'Lunas'),
(160000, 'Cash', 'Gagal'),
(162000, 'Debit', 'Lunas'),
(34000, 'QRIS', 'Lunas'),
(84000, 'Cash', 'Lunas'),
(108000, 'Debit', 'Pending'),
(240000, 'QRIS', 'Lunas'),
(50000, 'Cash', 'Lunas'),
(72000, 'Debit', 'Lunas'),
(15000, 'QRIS', 'Pending'),
(32000, 'Cash', 'Lunas'),
(51000, 'Debit', 'Gagal'),
(80000, 'QRIS', 'Lunas'),
(60000, 'Cash', 'Lunas'),
(84000, 'Debit', 'Lunas'),
(13000, 'QRIS', 'Pending'),
(24000, 'Cash', 'Lunas'),
(48000, 'Debit', 'Lunas'),
(36000, 'QRIS', 'Lunas'),
(70000, 'Cash', 'Pending'),
(60000, 'Debit', 'Lunas'),
(20000, 'QRIS', 'Gagal'),
(44000, 'Cash', 'Lunas'),
(69000, 'Debit', 'Lunas'),
(64000, 'QRIS', 'Lunas'),
(75000, 'Cash', 'Pending'),
(90000, 'Debit', 'Lunas');

INSERT INTO pemesanan (id_customer, id_pembayaran, titik_pemesanan, deskripsi_pesanan, status_pemesanan, jumlah, tanggal_pemesanan) VALUES
(1, 1,  'Meja 1',  'Pesanan makan siang',         'Selesai',    2, '2026-08-01 12:15:00'),
(2, 2,  NULL,      'Pesanan take away',           'Selesai',    1, '2026-08-05 18:30:00'),
(3, 3,  'Meja 3',  'Pesanan untuk 4 orang',       'Selesai',    4, '2026-08-10 19:45:00'),
(4, 4,  'Meja 4',  'Pesanan menunggu pembayaran', 'Diproses',   2, '2026-08-15 13:00:00'),
(5, 5,  NULL,      'Pesanan delivery',            'Selesai',    3, '2026-08-20 20:10:00'),
(6, 6,  'Meja 6',  'Pesanan sarapan pagi',        'Selesai',    1, '2026-08-25 08:20:00'),
(7, 7,  'Meja 7',  'Pesanan acara ulang tahun',   'Selesai',    6, '2026-09-01 17:00:00'),
(8, 8,  NULL,      'Pesanan sedang diproses',     'Diproses',   2, '2026-09-05 12:40:00'),
(9, 9,  'Meja 9',  'Pesanan makan malam',         'Selesai',    3, '2026-09-10 19:00:00'),
(10,10, 'Meja 10', 'Pesanan gagal bayar',         'Dibatalkan', 1, '2026-09-12 21:15:00'),
(11, 11, 'Meja 11', 'Pesanan sarapan pagi', 'Selesai', 6, '2026-09-14 03:00:00'),
(12, 12, 'Meja 12', 'Pesanan delivery', 'Selesai', 1, '2026-09-14 22:00:00'),
(13, 13, 'Meja 13', 'Pesanan arisan keluarga', 'Selesai', 2, '2026-09-15 17:00:00'),
(14, 14, NULL, 'Pesanan rapat kantor', 'Diproses', 3, '2026-09-16 12:00:00'),
(15, 15, 'Meja 15', 'Pesanan acara ulang tahun', 'Selesai', 4, '2026-09-17 07:00:00'),
(16, 16, 'Meja 16', 'Pesanan makan siang', 'Selesai', 5, '2026-09-18 02:00:00'),
(17, 17, 'Meja 17', 'Pesanan take away', 'Selesai', 6, '2026-09-18 21:00:00'),
(18, 18, NULL, 'Pesanan makan malam', 'Diproses', 1, '2026-09-19 16:00:00'),
(19, 19, 'Meja 19', 'Pesanan sarapan pagi', 'Selesai', 2, '2026-09-20 11:00:00'),
(20, 20, 'Meja 20', 'Pesanan delivery', 'Dibatalkan', 3, '2026-09-21 06:00:00'),
(21, 21, 'Meja 21', 'Pesanan arisan keluarga', 'Selesai', 4, '2026-09-22 01:00:00'),
(22, 22, NULL, 'Pesanan rapat kantor', 'Selesai', 5, '2026-09-22 20:00:00'),
(23, 23, 'Meja 23', 'Pesanan acara ulang tahun', 'Selesai', 6, '2026-09-23 15:00:00'),
(24, 24, 'Meja 24', 'Pesanan makan siang', 'Diproses', 1, '2026-09-24 10:00:00'),
(25, 25, 'Meja 25', 'Pesanan take away', 'Selesai', 2, '2026-09-25 05:00:00'),
(26, 26, NULL, 'Pesanan makan malam', 'Selesai', 3, '2026-09-26 00:00:00'),
(27, 27, 'Meja 27', 'Pesanan sarapan pagi', 'Selesai', 4, '2026-09-26 19:00:00'),
(28, 28, 'Meja 28', 'Pesanan delivery', 'Diproses', 5, '2026-09-27 14:00:00'),
(29, 29, 'Meja 29', 'Pesanan arisan keluarga', 'Selesai', 6, '2026-09-28 09:00:00'),
(30, 30, NULL, 'Pesanan rapat kantor', 'Dibatalkan', 1, '2026-09-29 04:00:00'),
(31, 31, 'Meja 31', 'Pesanan acara ulang tahun', 'Selesai', 2, '2026-09-29 23:00:00'),
(32, 32, 'Meja 32', 'Pesanan makan siang', 'Selesai', 3, '2026-09-30 18:00:00'),
(33, 33, 'Meja 33', 'Pesanan take away', 'Selesai', 4, '2026-10-01 13:00:00'),
(34, 34, NULL, 'Pesanan makan malam', 'Diproses', 5, '2026-10-02 08:00:00'),
(35, 35, 'Meja 35', 'Pesanan sarapan pagi', 'Selesai', 6, '2026-10-03 03:00:00'),
(36, 36, 'Meja 36', 'Pesanan delivery', 'Selesai', 1, '2026-10-03 22:00:00'),
(37, 37, 'Meja 37', 'Pesanan arisan keluarga', 'Selesai', 2, '2026-10-04 17:00:00'),
(38, 38, NULL, 'Pesanan rapat kantor', 'Diproses', 3, '2026-10-05 12:00:00'),
(39, 39, 'Meja 39', 'Pesanan acara ulang tahun', 'Selesai', 4, '2026-10-06 07:00:00'),
(40, 40, 'Meja 40', 'Pesanan makan siang', 'Dibatalkan', 5, '2026-10-07 02:00:00'),
(41, 41, 'Meja 41', 'Pesanan take away', 'Selesai', 6, '2026-10-07 21:00:00'),
(42, 42, NULL, 'Pesanan makan malam', 'Selesai', 1, '2026-10-08 16:00:00'),
(43, 43, 'Meja 43', 'Pesanan sarapan pagi', 'Selesai', 2, '2026-10-09 11:00:00'),
(44, 44, 'Meja 44', 'Pesanan delivery', 'Diproses', 3, '2026-10-10 06:00:00'),
(45, 45, 'Meja 45', 'Pesanan arisan keluarga', 'Selesai', 4, '2026-10-11 01:00:00'),
(46, 46, NULL, 'Pesanan rapat kantor', 'Selesai', 5, '2026-10-11 20:00:00'),
(47, 47, 'Meja 47', 'Pesanan acara ulang tahun', 'Selesai', 6, '2026-10-12 15:00:00'),
(48, 48, 'Meja 48', 'Pesanan makan siang', 'Diproses', 1, '2026-10-13 10:00:00'),
(49, 49, 'Meja 49', 'Pesanan take away', 'Selesai', 2, '2026-10-14 05:00:00'),
(50, 50, NULL, 'Pesanan makan malam', 'Dibatalkan', 3, '2026-10-15 00:00:00'),
(51, 51, 'Meja 51', 'Pesanan sarapan pagi', 'Selesai', 4, '2026-10-15 19:00:00'),
(52, 52, 'Meja 52', 'Pesanan delivery', 'Selesai', 5, '2026-10-16 14:00:00'),
(53, 53, 'Meja 53', 'Pesanan arisan keluarga', 'Selesai', 6, '2026-10-17 09:00:00'),
(54, 54, NULL, 'Pesanan rapat kantor', 'Diproses', 1, '2026-10-18 04:00:00'),
(55, 55, 'Meja 55', 'Pesanan acara ulang tahun', 'Selesai', 2, '2026-10-18 23:00:00'),
(56, 56, 'Meja 56', 'Pesanan makan siang', 'Selesai', 3, '2026-10-19 18:00:00'),
(57, 57, 'Meja 57', 'Pesanan take away', 'Selesai', 4, '2026-10-20 13:00:00'),
(58, 58, NULL, 'Pesanan makan malam', 'Diproses', 5, '2026-10-21 08:00:00'),
(59, 59, 'Meja 59', 'Pesanan sarapan pagi', 'Selesai', 6, '2026-10-22 03:00:00'),
(60, 60, 'Meja 60', 'Pesanan delivery', 'Dibatalkan', 1, '2026-10-22 22:00:00'),
(61, 61, 'Meja 61', 'Pesanan arisan keluarga', 'Selesai', 2, '2026-10-23 17:00:00'),
(62, 62, NULL, 'Pesanan rapat kantor', 'Selesai', 3, '2026-10-24 12:00:00'),
(63, 63, 'Meja 63', 'Pesanan acara ulang tahun', 'Selesai', 4, '2026-10-25 07:00:00'),
(64, 64, 'Meja 64', 'Pesanan makan siang', 'Diproses', 5, '2026-10-26 02:00:00'),
(65, 65, 'Meja 65', 'Pesanan take away', 'Selesai', 6, '2026-10-26 21:00:00');

INSERT INTO detail_pemesanan (id_pemesanan, id_menu, jumlah_item) VALUES
(1, 1, 2),
(2, 8, 1),
(3, 4, 4),
(4, 2, 2),
(5, 6, 3),
(6, 7, 1),
(7, 5, 6),
(8, 9, 2),
(9, 10, 3),
(10, 3, 1),
(11, 11, 6),
(12, 12, 1),
(13, 13, 2),
(14, 14, 3),
(15, 15, 4),
(16, 16, 5),
(17, 17, 6),
(18, 18, 1),
(19, 19, 2),
(20, 20, 3),
(21, 21, 4),
(22, 22, 5),
(23, 23, 6),
(24, 24, 1),
(25, 25, 2),
(26, 26, 3),
(27, 27, 4),
(28, 28, 5),
(29, 29, 6),
(30, 30, 1),
(31, 31, 2),
(32, 32, 3),
(33, 33, 4),
(34, 34, 5),
(35, 35, 6),
(36, 36, 1),
(37, 37, 2),
(38, 38, 3),
(39, 39, 4),
(40, 40, 5),
(41, 41, 6),
(42, 42, 1),
(43, 43, 2),
(44, 44, 3),
(45, 45, 4),
(46, 46, 5),
(47, 47, 6),
(48, 48, 1),
(49, 49, 2),
(50, 50, 3),
(51, 51, 4),
(52, 52, 5),
(53, 53, 6),
(54, 54, 1),
(55, 55, 2),
(56, 56, 3),
(57, 57, 4),
(58, 58, 5),
(59, 59, 6),
(60, 60, 1),
(61, 61, 2),
(62, 62, 3),
(63, 63, 4),
(64, 64, 5),
(65, 65, 6);

INSERT INTO diproses_oleh (id_pemesanan, id_staff, id_pelayan) VALUES
(1, 1, 'PLY-01'),
(2, 2, 'PLY-02'),
(3, 3, 'PLY-03'),
(4, 4, 'PLY-04'),
(5, 5, 'PLY-05'),
(6, 1, 'PLY-06'),
(7, 2, 'PLY-07'),
(8, 3, 'PLY-08'),
(9, 4, 'PLY-09'),
(10, 5, 'PLY-10'),
(11, 17, 'PLY-11'),
(12, 18, 'PLY-12'),
(13, 19, 'PLY-13'),
(14, 20, 'PLY-14'),
(15, 21, 'PLY-15'),
(16, 22, 'PLY-16'),
(17, 23, 'PLY-17'),
(18, 24, 'PLY-18'),
(19, 25, 'PLY-19'),
(20, 26, 'PLY-20'),
(21, 27, 'PLY-21'),
(22, 28, 'PLY-22'),
(23, 29, 'PLY-23'),
(24, 30, 'PLY-24'),
(25, 31, 'PLY-25'),
(26, 32, 'PLY-26'),
(27, 33, 'PLY-27'),
(28, 34, 'PLY-28'),
(29, 35, 'PLY-29'),
(30, 36, 'PLY-30'),
(31, 37, 'PLY-31'),
(32, 38, 'PLY-32'),
(33, 39, 'PLY-33'),
(34, 40, 'PLY-34'),
(35, 1, 'PLY-35'),
(36, 2, 'PLY-36'),
(37, 3, 'PLY-37'),
(38, 4, 'PLY-38'),
(39, 5, 'PLY-39'),
(40, 11, 'PLY-40'),
(41, 12, 'PLY-41'),
(42, 13, 'PLY-42'),
(43, 14, 'PLY-43'),
(44, 15, 'PLY-44'),
(45, 16, 'PLY-45'),
(46, 17, 'PLY-46'),
(47, 18, 'PLY-47'),
(48, 19, 'PLY-48'),
(49, 20, 'PLY-49'),
(50, 21, 'PLY-50'),
(51, 22, 'PLY-51'),
(52, 23, 'PLY-52'),
(53, 24, 'PLY-53'),
(54, 25, 'PLY-54'),
(55, 26, 'PLY-55'),
(56, 27, 'PLY-56'),
(57, 28, 'PLY-57'),
(58, 29, 'PLY-58'),
(59, 30, 'PLY-59'),
(60, 31, 'PLY-60'),
(61, 32, 'PLY-61'),
(62, 33, 'PLY-62'),
(63, 34, 'PLY-63'),
(64, 35, 'PLY-64'),
(65, 36, 'PLY-65');

INSERT INTO nota (id_pembayaran, nomor_antrian, list_menu) VALUES
(1,  'A01', 'Nasi Goreng Spesial x2'),
(2,  'A02', 'Es Teh Manis x1'),
(3,  'A03', 'Sate Ayam Madura x4'),
(4,  'A04', 'Mie Ayam Bakso x2'),
(5,  'A05', 'Gado-Gado Jakarta x3'),
(6,  'A06', 'Nasi Uduk Komplit x1'),
(7,  'A07', 'Soto Betawi x6'),
(8,  'A08', 'Es Jeruk Peras x2'),
(9,  'A09', 'Kopi Susu Gula Aren x3'),
(10, 'A10', 'Ayam Geprek Sambal Matah x1'),
(11, 'A11', 'Bakso Urat Jumbo x6'),
(12, 'A12', 'Bakso Beranak x1'),
(13, 'A13', 'Rawon Daging x2'),
(14, 'A14', 'Rendang Sapi x3'),
(15, 'A15', 'Gudeg Jogja Komplit x4'),
(16, 'A16', 'Pecel Lele x5'),
(17, 'A17', 'Pecel Ayam x6'),
(18, 'A18', 'Soto Ayam Lamongan x1'),
(19, 'A19', 'Soto Kudus x2'),
(20, 'A20', 'Tongseng Kambing x3'),
(21, 'A21', 'Gulai Kambing x4'),
(22, 'A22', 'Sop Buntut x5'),
(23, 'A23', 'Iga Bakar Madu x6'),
(24, 'A24', 'Ayam Bakar Taliwang x1'),
(25, 'A25', 'Ayam Goreng Kremes x2'),
(26, 'A26', 'Bebek Goreng Sambal Ijo x3'),
(27, 'A27', 'Nasi Liwet Solo x4'),
(28, 'A28', 'Nasi Pecel Madiun x5'),
(29, 'A29', 'Nasi Campur Bali x6'),
(30, 'A30', 'Nasi Kuning Tumpeng Mini x1'),
(31, 'A31', 'Lontong Sayur x2'),
(32, 'A32', 'Ketoprak x3'),
(33, 'A33', 'Tahu Tek x4'),
(34, 'A34', 'Tahu Gejrot x5'),
(35, 'A35', 'Siomay Bandung x6'),
(36, 'A36', 'Batagor x1'),
(37, 'A37', 'Pempek Kapal Selam x2'),
(38, 'A38', 'Mie Goreng Jawa x3'),
(39, 'A39', 'Mie Kuah Nyemek x4'),
(40, 'A40', 'Kwetiau Goreng Seafood x5'),
(41, 'A41', 'Capcay Goreng x6'),
(42, 'A42', 'Cumi Goreng Tepung x1'),
(43, 'A43', 'Udang Saus Padang x2'),
(44, 'A44', 'Ikan Bakar Jimbaran x3'),
(45, 'A45', 'Gurame Asam Manis x4'),
(46, 'A46', 'Tempe Mendoan x5'),
(47, 'A47', 'Tahu Crispy x6'),
(48, 'A48', 'Pisang Goreng Keju x1'),
(49, 'A49', 'Kentang Goreng x2'),
(50, 'A50', 'Roti Bakar Cokelat x3'),
(51, 'A51', 'Martabak Mini Manis x4'),
(52, 'A52', 'Es Cendol Dawet x5'),
(53, 'A53', 'Es Campur x6'),
(54, 'A54', 'Es Doger x1'),
(55, 'A55', 'Es Kelapa Muda x2'),
(56, 'A56', 'Es Teler x3'),
(57, 'A57', 'Wedang Jahe x4'),
(58, 'A58', 'Teh Tarik x5'),
(59, 'A59', 'Kopi Hitam Tubruk x6'),
(60, 'A60', 'Cappuccino x1'),
(61, 'A61', 'Latte Caramel x2'),
(62, 'A62', 'Matcha Latte x3'),
(63, 'A63', 'Jus Alpukat x4'),
(64, 'A64', 'Jus Mangga x5'),
(65, 'A65', 'Jus Jambu Merah x6');

-- Tutup akses API sejak impor pertama, sebelum migrasi policy dijalankan.
DO $$ DECLARE t text; BEGIN
  FOREACH t IN ARRAY ARRAY['customer','menu','staff','staff_kitchen','kurir','ulasan','pelayan','pembayaran','pemesanan','detail_pemesanan','diproses_oleh','nota'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon,authenticated',t);
  END LOOP;
END $$;
COMMIT;
