BEGIN;
SET LOCAL search_path = public, pg_catalog;
ALTER TABLE public.menu ADD COLUMN category text NOT NULL DEFAULT 'Belum dikategorikan',
  ADD COLUMN is_active boolean NOT NULL DEFAULT true,
  ADD CONSTRAINT menu_price_valid CHECK (harga_satuan BETWEEN 0 AND 1000000000);
ALTER TABLE public.customer ADD COLUMN is_active boolean NOT NULL DEFAULT true;
ALTER TABLE public.staff ADD COLUMN is_active boolean NOT NULL DEFAULT true;
ALTER TABLE public.pelayan ADD COLUMN is_active boolean NOT NULL DEFAULT true;
ALTER TABLE public.pemesanan ALTER COLUMN id_customer DROP NOT NULL,
  ALTER COLUMN status_pemesanan SET DEFAULT 'Menunggu',
  ADD COLUMN jenis_pesanan text NOT NULL DEFAULT 'unknown' CHECK (jenis_pesanan IN ('unknown','dine_in','takeaway','delivery')),
  ADD COLUMN created_by uuid REFERENCES auth.users(id),
  ADD COLUMN request_id uuid UNIQUE,
  ADD COLUMN created_at timestamptz;
-- Timestamp sumber tidak memiliki zona. Asumsi eksplisit: waktu sumber adalah Asia/Jakarta.
UPDATE public.pemesanan SET created_at = tanggal_pemesanan AT TIME ZONE 'Asia/Jakarta';
ALTER TABLE public.pemesanan ALTER COLUMN created_at SET DEFAULT now(), ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE public.pemesanan ADD CONSTRAINT order_status_valid CHECK (status_pemesanan IN ('Menunggu','Diproses','Siap','Selesai','Dibatalkan'));
ALTER TABLE public.pembayaran ALTER COLUMN status_pembayaran SET DEFAULT 'Pending',
  ADD COLUMN amount_received bigint,
  ADD COLUMN paid_at timestamptz,
  ADD CONSTRAINT payment_total_valid CHECK (total_harga >= 0),
  ADD CONSTRAINT payment_status_valid CHECK (status_pembayaran IN ('Pending','Lunas','Gagal')),
  ADD CONSTRAINT payment_method_valid CHECK (pay_method IN ('Cash','Debit','QRIS'));
ALTER TABLE public.detail_pemesanan ADD COLUMN harga_satuan_saat_transaksi bigint,
  ADD CONSTRAINT quantity_valid CHECK (jumlah_item BETWEEN 1 AND 99),
  ADD CONSTRAINT snapshot_price_valid CHECK (harga_satuan_saat_transaksi BETWEEN 0 AND 1000000000);
ALTER TABLE public.nota ALTER COLUMN list_menu TYPE text;
CREATE SEQUENCE public.rf_queue_seq START WITH 66 MAXVALUE 999999999;
ALTER TABLE public.nota ADD CONSTRAINT receipt_payment_unique UNIQUE(id_pembayaran),
  ADD CONSTRAINT queue_unique UNIQUE(nomor_antrian);
CREATE TABLE public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 100),
  role text NOT NULL CHECK (role IN ('admin','cashier','kitchen','waiter','courier')),
  id_staff integer REFERENCES public.staff(id_staff),
  id_pelayan varchar(10) REFERENCES public.pelayan(id_pelayan),
  is_active boolean NOT NULL DEFAULT true,
  CHECK (NOT (id_staff IS NOT NULL AND id_pelayan IS NOT NULL))
);
CREATE UNIQUE INDEX profiles_staff_unique ON public.profiles(id_staff) WHERE id_staff IS NOT NULL;
CREATE UNIQUE INDEX profiles_waiter_unique ON public.profiles(id_pelayan) WHERE id_pelayan IS NOT NULL;
CREATE TABLE public.pengiriman (
  id_pengiriman integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_pemesanan integer NOT NULL UNIQUE REFERENCES public.pemesanan(id_pemesanan),
  id_kurir integer REFERENCES public.kurir(id_kurir),
  alamat_pengiriman text NOT NULL CHECK (length(btrim(alamat_pengiriman)) BETWEEN 1 AND 1000),
  catatan_pengiriman text NOT NULL DEFAULT '',
  status_pengiriman text NOT NULL DEFAULT 'Menunggu' CHECK (status_pengiriman IN ('Menunggu','Ditugaskan','Diambil','Terkirim','Dibatalkan')),
  waktu_ditugaskan timestamptz, waktu_diambil timestamptz, waktu_sampai timestamptz
);
CREATE TABLE public.restaurant_settings (
  id integer PRIMARY KEY CHECK (id=1),
  display_name text NOT NULL DEFAULT 'RestoFlow',
  receipt_footer text NOT NULL DEFAULT 'Terima kasih atas kunjungan Anda.'
);
INSERT INTO public.restaurant_settings(id) VALUES (1);
CREATE TABLE public.audit_log (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor uuid REFERENCES auth.users(id), action text NOT NULL,
  entity_id text, details jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX order_date_idx ON public.pemesanan(created_at);
CREATE INDEX order_table_idx ON public.pemesanan(id_customer);
CREATE INDEX order_status_idx ON public.pemesanan(status_pemesanan);
CREATE INDEX assignment_staff_idx ON public.diproses_oleh(id_staff);
CREATE INDEX assignment_waiter_idx ON public.diproses_oleh(id_pelayan);
CREATE INDEX delivery_courier_idx ON public.pengiriman(id_kurir);
CREATE INDEX review_courier_idx ON public.ulasan(id_kurir);
CREATE INDEX detail_menu_idx ON public.detail_pemesanan(id_menu);
DO $$ DECLARE t text; BEGIN
  FOREACH t IN ARRAY ARRAY['profiles','pengiriman','restaurant_settings','audit_log'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon,authenticated',t);
  END LOOP;
END $$;
COMMIT;
