-- RestoFlow 1.0.3: pengelompokan 65 menu yang diperiksa pada 13 November 2026.
-- Makanan: ID 1-7 dan 11-51 (termasuk camilan).
-- Minuman: ID 8-10 dan 52-65 (termasuk es cendol/campur/doger/teler).
-- Tidak mengubah file sumber asli, harga, nama, deskripsi, status aktif atau transaksi.
BEGIN;
DO $$
DECLARE
  identity_check text;
  original_rows jsonb;
  preserved_rows jsonb;
BEGIN
  PERFORM 1 FROM public.menu WHERE id_menu BETWEEN 1 AND 65 FOR UPDATE;
  SELECT md5(string_agg(id_menu::text || ':' || nama_menu, '|' ORDER BY id_menu)),
         jsonb_agg(to_jsonb(m) - 'category' ORDER BY id_menu)
    INTO identity_check, original_rows
    FROM public.menu m WHERE id_menu BETWEEN 1 AND 65;
  IF identity_check IS DISTINCT FROM 'bb4b5f48daefca1de9a3439be178bb49' THEN
    RAISE EXCEPTION 'Daftar menu berbeda dari daftar yang diperiksa. Tinjau pemetaan sebelum menjalankan.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.menu WHERE id_menu BETWEEN 1 AND 65
    AND category NOT IN ('Belum dikategorikan',
      CASE WHEN id_menu IN (8,9,10) OR id_menu BETWEEN 52 AND 65 THEN 'Minuman' ELSE 'Makanan' END)
  ) THEN
    RAISE EXCEPTION 'Ada kategori berbeda yang perlu ditinjau; tidak ditimpa otomatis.';
  END IF;
  UPDATE public.menu
    SET category = CASE WHEN id_menu IN (8,9,10) OR id_menu BETWEEN 52 AND 65
                   THEN 'Minuman' ELSE 'Makanan' END
    WHERE id_menu BETWEEN 1 AND 65 AND category = 'Belum dikategorikan';
  SELECT jsonb_agg(to_jsonb(m) - 'category' ORDER BY id_menu)
    INTO preserved_rows FROM public.menu m WHERE id_menu BETWEEN 1 AND 65;
  IF original_rows IS DISTINCT FROM preserved_rows THEN
    RAISE EXCEPTION 'Kolom selain kategori berubah; transaksi dibatalkan.';
  END IF;
  IF (SELECT count(*) FROM public.menu WHERE id_menu BETWEEN 1 AND 65 AND category='Makanan') <> 48
     OR (SELECT count(*) FROM public.menu WHERE id_menu BETWEEN 1 AND 65 AND category='Minuman') <> 17 THEN
    RAISE EXCEPTION 'Jumlah kategori tidak sesuai; transaksi dibatalkan.';
  END IF;
END $$;
COMMIT;
