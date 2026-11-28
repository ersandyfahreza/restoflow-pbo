BEGIN;

-- Mengizinkan perubahan status pesanan tanpa syarat urutan (maju maupun mundur bila ada kesalahan).
DO $$
DECLARE
  definition text;
  old_block text;
  new_block text;
BEGIN
  definition := pg_get_functiondef('restoflow_private.rf_execute_impl(text,jsonb)'::regprocedure);
  old_block := $OLD$IF NOT ((o.status_pemesanan='Menunggu' AND next_status IN ('Diproses','Dibatalkan'))
        OR (o.status_pemesanan='Diproses' AND next_status IN ('Siap','Dibatalkan'))
        OR (o.status_pemesanan='Siap' AND next_status IN ('Selesai','Dibatalkan'))) THEN RAISE EXCEPTION 'RF: Perubahan status tidak valid. Muat ulang pesanan.'; END IF;$OLD$;
  new_block := $NEW$IF coalesce(next_status,'') NOT IN ('Menunggu','Diproses','Siap','Selesai','Dibatalkan') THEN
        RAISE EXCEPTION 'RF: Status pesanan tidak valid.';
      END IF;$NEW$;

  IF position(old_block IN definition) > 0 THEN
    definition := replace(definition, old_block, new_block);
  END IF;

  -- Izinkan peran dapur mengubah ke Diproses/Siap tanpa syarat status sebelumnya.
  definition := replace(
    definition,
    $OLD_K$IF r='kitchen' AND NOT ((o.status_pemesanan='Menunggu' AND next_status='Diproses') OR (o.status_pemesanan='Diproses' AND next_status='Siap')) THEN RAISE insufficient_privilege; END IF;$OLD_K$,
    $NEW_K$IF r='kitchen' AND next_status NOT IN ('Diproses','Siap') THEN RAISE insufficient_privilege; END IF;$NEW_K$
  );

  -- Izinkan peran pelayan mengubah ke Selesai tanpa syarat status sebelumnya.
  definition := replace(
    definition,
    $OLD_W$IF r='waiter' AND NOT (o.status_pemesanan='Siap' AND next_status='Selesai') THEN RAISE insufficient_privilege; END IF;$OLD_W$,
    $NEW_W$IF r='waiter' AND next_status<>'Selesai' THEN RAISE insufficient_privilege; END IF;$NEW_W$
  );

  -- Jika pesanan Dibatalkan dibuka kembali ke Menunggu/Diproses/Siap, pulihkan status pembayaran Gagal -> Pending.
  definition := replace(
    definition,
    $OLD2$IF next_status='Dibatalkan' THEN
        UPDATE public.pembayaran SET status_pembayaran='Gagal' WHERE id_pembayaran=b.id_pembayaran;
        UPDATE public.pengiriman SET status_pengiriman='Dibatalkan' WHERE id_pemesanan=oid;
      END IF;$OLD2$,
    $NEW2$IF next_status='Dibatalkan' THEN
        UPDATE public.pembayaran SET status_pembayaran='Gagal' WHERE id_pembayaran=b.id_pembayaran;
        UPDATE public.pengiriman SET status_pengiriman='Dibatalkan' WHERE id_pemesanan=oid;
      ELSIF o.status_pemesanan='Dibatalkan' AND next_status IN ('Menunggu','Diproses','Siap') THEN
        UPDATE public.pembayaran SET status_pembayaran='Pending' WHERE id_pembayaran=b.id_pembayaran AND status_pembayaran='Gagal';
        UPDATE public.pengiriman SET status_pengiriman='Menunggu' WHERE id_pemesanan=oid AND status_pengiriman='Dibatalkan';
      END IF;$NEW2$
  );

  EXECUTE definition;
END $$;

NOTIFY pgrst,'reload schema';
COMMIT;
