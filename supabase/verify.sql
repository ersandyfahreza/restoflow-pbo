-- Read-only verification after import. Counts include new records if application already used.
SELECT 'customer' AS tabel,count(*) AS jumlah FROM public.customer
UNION ALL SELECT 'menu',count(*) FROM public.menu
UNION ALL SELECT 'staff',count(*) FROM public.staff
UNION ALL SELECT 'staff_kitchen',count(*) FROM public.staff_kitchen
UNION ALL SELECT 'kurir',count(*) FROM public.kurir
UNION ALL SELECT 'ulasan',count(*) FROM public.ulasan
UNION ALL SELECT 'pelayan',count(*) FROM public.pelayan
UNION ALL SELECT 'pembayaran',count(*) FROM public.pembayaran
UNION ALL SELECT 'pemesanan',count(*) FROM public.pemesanan
UNION ALL SELECT 'detail_pemesanan',count(*) FROM public.detail_pemesanan
UNION ALL SELECT 'diproses_oleh',count(*) FROM public.diproses_oleh
UNION ALL SELECT 'nota',count(*) FROM public.nota;
SELECT status_pembayaran,count(*),sum(total_harga) FROM public.pembayaran GROUP BY status_pembayaran;
SELECT o.id_pemesanan,b.total_harga AS total_historis,
  sum(coalesce(d.harga_satuan_saat_transaksi,m.harga_satuan)::bigint*d.jumlah_item) AS rincian_pembanding,
  b.total_harga-sum(coalesce(d.harga_satuan_saat_transaksi,m.harga_satuan)::bigint*d.jumlah_item) AS selisih
FROM public.pemesanan o JOIN public.pembayaran b USING(id_pembayaran)
JOIN public.detail_pemesanan d USING(id_pemesanan) JOIN public.menu m USING(id_menu)
GROUP BY o.id_pemesanan,b.total_harga
HAVING b.total_harga<>sum(coalesce(d.harga_satuan_saat_transaksi,m.harga_satuan)::bigint*d.jumlah_item)
ORDER BY o.id_pemesanan;
SELECT count(*) AS orphan_detail FROM public.detail_pemesanan d
LEFT JOIN public.pemesanan o USING(id_pemesanan) LEFT JOIN public.menu m USING(id_menu)
WHERE o.id_pemesanan IS NULL OR m.id_menu IS NULL;
SELECT tablename,rowsecurity FROM pg_tables WHERE schemaname='public' ORDER BY tablename;
