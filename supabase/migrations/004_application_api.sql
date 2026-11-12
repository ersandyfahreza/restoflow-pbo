BEGIN;
ALTER TABLE public.pemesanan ADD COLUMN request_payload jsonb;
ALTER TABLE public.customer ADD CONSTRAINT table_qr_unique UNIQUE(qr_meja), ADD CONSTRAINT table_number_unique UNIQUE(nomor_meja);

CREATE FUNCTION public.rf_write_lines(order_id integer, items jsonb) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE item jsonb; menu_row public.menu; qty integer; total bigint:=0;
BEGIN
  IF jsonb_typeof(items) IS DISTINCT FROM 'array' OR jsonb_array_length(items) NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'RF: Pilih 1–100 menu berbeda.';
  END IF;
  IF (SELECT count(DISTINCT (x->>'menu_id')::integer) FROM jsonb_array_elements(items) x) <> jsonb_array_length(items) THEN
    RAISE EXCEPTION 'RF: Menu duplikat dalam pesanan.';
  END IF;
  DELETE FROM public.detail_pemesanan WHERE id_pemesanan=order_id;
  FOR item IN SELECT x FROM jsonb_array_elements(items) x ORDER BY (x->>'menu_id')::integer LOOP
    qty := (item->>'quantity')::integer;
    IF qty IS NULL OR qty NOT BETWEEN 1 AND 99 THEN RAISE EXCEPTION 'RF: Jumlah menu harus 1–99.'; END IF;
    SELECT * INTO menu_row FROM public.menu WHERE id_menu=(item->>'menu_id')::integer AND is_active FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'RF: Menu tidak ditemukan atau sudah dinonaktifkan.'; END IF;
    total := total + menu_row.harga_satuan::bigint * qty;
    INSERT INTO public.detail_pemesanan(id_pemesanan,id_menu,jumlah_item,harga_satuan_saat_transaksi)
      VALUES(order_id,menu_row.id_menu,qty,menu_row.harga_satuan);
  END LOOP;
  RETURN total;
END $$;
REVOKE ALL ON FUNCTION public.rf_write_lines(integer,jsonb) FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.rf_execute(p_action text,p jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE r text:=public.rf_role(); actor uuid:=auth.uid(); oid integer; pid integer; rid uuid;
  o public.pemesanan; b public.pembayaran; g public.pengiriman; existing public.profiles;
  total bigint; next_status text; kind text; table_id integer; kitchen integer; waiter text;
  result jsonb:='{}'; entity text; item_id integer; staff_id integer; new_role text;
BEGIN
  IF actor IS NULL OR r IS NULL THEN RAISE EXCEPTION 'RF: Akun belum memiliki profil aktif. Hubungi admin.'; END IF;
  IF p IS NULL OR jsonb_typeof(p)<>'object' THEN RAISE EXCEPTION 'RF: Data permintaan tidak valid.'; END IF;

  IF p_action IN ('create_order','update_order') THEN
    IF r NOT IN ('admin','cashier','waiter') THEN RAISE insufficient_privilege; END IF;
    kind:=p->>'type'; table_id:=(p->>'table_id')::integer;
    IF kind IS NULL OR kind NOT IN ('dine_in','takeaway','delivery') THEN RAISE EXCEPTION 'RF: Jenis pesanan tidak valid.'; END IF;
    IF length(coalesce(p->>'notes',''))>2000 THEN RAISE EXCEPTION 'RF: Catatan maksimal 2000 karakter.'; END IF;
    IF p_action='create_order' THEN
      rid:=(p->>'request_id')::uuid;
      IF rid IS NULL THEN RAISE EXCEPTION 'RF: ID permintaan wajib diisi.'; END IF;
      PERFORM pg_advisory_xact_lock(hashtextextended(rid::text,0));
      SELECT * INTO o FROM public.pemesanan WHERE request_id=rid;
      IF FOUND THEN
        IF o.created_by<>actor OR o.request_payload IS DISTINCT FROM p THEN RAISE EXCEPTION 'RF: ID permintaan sudah dipakai dengan data berbeda.'; END IF;
        RETURN jsonb_build_object('id',o.id_pemesanan,'replayed',true);
      END IF;
    ELSE
      SELECT * INTO o FROM public.pemesanan WHERE id_pemesanan=(p->>'id')::integer FOR UPDATE;
      IF NOT FOUND OR NOT public.rf_can_order(o.id_pemesanan) THEN RAISE insufficient_privilege; END IF;
      SELECT * INTO b FROM public.pembayaran WHERE id_pembayaran=o.id_pembayaran FOR UPDATE;
      IF o.status_pemesanan<>'Menunggu' OR b.status_pembayaran<>'Pending' THEN RAISE EXCEPTION 'RF: Hanya pesanan Menunggu yang belum dibayar dapat diedit.'; END IF;
      oid:=o.id_pemesanan; pid:=o.id_pembayaran;
    END IF;
    IF kind='dine_in' THEN
      PERFORM 1 FROM public.customer WHERE id_customer=table_id AND is_active FOR UPDATE;
      IF NOT FOUND THEN RAISE EXCEPTION 'RF: Pilih meja aktif.'; END IF;
      IF EXISTS(SELECT 1 FROM public.pemesanan WHERE id_customer=table_id AND id_pemesanan<>coalesce(oid,0)
        AND status_pemesanan NOT IN ('Selesai','Dibatalkan')) THEN RAISE EXCEPTION 'RF: Meja masih memiliki pesanan aktif.'; END IF;
    ELSE table_id:=NULL; END IF;
    IF kind='delivery' AND length(btrim(coalesce(p->>'address',''))) NOT BETWEEN 1 AND 1000 THEN
      RAISE EXCEPTION 'RF: Alamat pengiriman wajib diisi, maksimal 1000 karakter.';
    END IF;
    IF p_action='create_order' THEN
      INSERT INTO public.pembayaran(total_harga,pay_method) VALUES(0,'Cash') RETURNING id_pembayaran INTO pid;
      INSERT INTO public.pemesanan(id_customer,id_pembayaran,titik_pemesanan,deskripsi_pesanan,status_pemesanan,jumlah,
        tanggal_pemesanan,jenis_pesanan,created_by,request_id,request_payload)
      VALUES(table_id,pid,kind,coalesce(p->>'notes',''),'Menunggu',1,now() AT TIME ZONE 'Asia/Jakarta',kind,actor,rid,p)
        RETURNING id_pemesanan INTO oid;
    ELSE
      UPDATE public.pemesanan SET id_customer=table_id,titik_pemesanan=kind,jenis_pesanan=kind,
        deskripsi_pesanan=coalesce(p->>'notes','') WHERE id_pemesanan=oid;
    END IF;
    total:=public.rf_write_lines(oid,p->'items');
    UPDATE public.pembayaran SET total_harga=total WHERE id_pembayaran=pid;
    kitchen:=(p->>'kitchen_id')::integer; waiter:=nullif(p->>'waiter','');
    IF (kitchen IS NULL)<>(waiter IS NULL) THEN RAISE EXCEPTION 'RF: Pilih staf dapur dan pelayan bersama, atau kosongkan keduanya.'; END IF;
    IF kitchen IS NOT NULL THEN
      PERFORM 1 FROM public.staff_kitchen k JOIN public.staff s USING(id_staff) WHERE k.id_staff=kitchen AND s.is_active;
      IF NOT FOUND THEN RAISE EXCEPTION 'RF: Staf dapur tidak aktif.'; END IF;
      PERFORM 1 FROM public.pelayan WHERE id_pelayan=waiter AND is_active;
      IF NOT FOUND THEN RAISE EXCEPTION 'RF: Pelayan tidak aktif.'; END IF;
    END IF;
    DELETE FROM public.diproses_oleh WHERE id_pemesanan=oid;
    IF kitchen IS NOT NULL THEN INSERT INTO public.diproses_oleh VALUES(oid,kitchen,waiter); END IF;
    IF kind='delivery' THEN
      INSERT INTO public.pengiriman(id_pemesanan,alamat_pengiriman) VALUES(oid,btrim(p->>'address'))
        ON CONFLICT(id_pemesanan) DO UPDATE SET alamat_pengiriman=excluded.alamat_pengiriman;
    ELSE DELETE FROM public.pengiriman WHERE id_pemesanan=oid; END IF;
    IF p_action='create_order' THEN
      INSERT INTO public.nota(id_pembayaran,nomor_antrian,list_menu) VALUES(pid,'B'||nextval('public.rf_queue_seq'), '');
    END IF;
    UPDATE public.nota SET list_menu=(SELECT string_agg(m.nama_menu||' x'||d.jumlah_item,', ' ORDER BY d.id_menu)
      FROM public.detail_pemesanan d JOIN public.menu m USING(id_menu) WHERE d.id_pemesanan=oid) WHERE id_pembayaran=pid;
    result:=jsonb_build_object('id',oid,'total',total); entity:=oid::text;

  ELSIF p_action IN ('order_status','pay','assign_delivery','delivery_status') THEN
    SELECT * INTO o FROM public.pemesanan WHERE id_pemesanan=(p->>'id')::integer FOR UPDATE;
    IF NOT FOUND OR NOT public.rf_can_order(o.id_pemesanan) THEN RAISE insufficient_privilege; END IF;
    SELECT * INTO b FROM public.pembayaran WHERE id_pembayaran=o.id_pembayaran FOR UPDATE;
    oid:=o.id_pemesanan; entity:=oid::text;
    IF p_action='pay' THEN
      IF r NOT IN ('admin','cashier') THEN RAISE insufficient_privilege; END IF;
      IF o.status_pemesanan='Dibatalkan' OR b.status_pembayaran<>'Pending' THEN RAISE EXCEPTION 'RF: Pesanan dibatalkan atau pembayaran sudah diproses.'; END IF;
      IF coalesce(p->>'method','') NOT IN ('Cash','Debit','QRIS') THEN RAISE EXCEPTION 'RF: Metode pembayaran tidak valid.'; END IF;
      total:=(p->>'received')::bigint;
      IF total IS NULL OR total<b.total_harga OR total>1000000000000000 THEN RAISE EXCEPTION 'RF: Nominal diterima tidak cukup atau melebihi batas.'; END IF;
      IF p->>'method'<>'Cash' AND total<>b.total_harga THEN RAISE EXCEPTION 'RF: Konfirmasi non-tunai harus sesuai nominal tagihan.'; END IF;
      UPDATE public.pembayaran SET status_pembayaran='Lunas',pay_method=p->>'method',amount_received=total,paid_at=now() WHERE id_pembayaran=b.id_pembayaran;
      result:=jsonb_build_object('change',total-b.total_harga);
    ELSIF p_action='order_status' THEN
      next_status:=p->>'status';
      IF r NOT IN ('admin','cashier','kitchen','waiter') THEN RAISE insufficient_privilege; END IF;
      IF r='kitchen' AND NOT ((o.status_pemesanan='Menunggu' AND next_status='Diproses') OR (o.status_pemesanan='Diproses' AND next_status='Siap')) THEN RAISE insufficient_privilege; END IF;
      IF r='waiter' AND NOT (o.status_pemesanan='Siap' AND next_status='Selesai') THEN RAISE insufficient_privilege; END IF;
      IF NOT ((o.status_pemesanan='Menunggu' AND next_status IN ('Diproses','Dibatalkan'))
        OR (o.status_pemesanan='Diproses' AND next_status IN ('Siap','Dibatalkan'))
        OR (o.status_pemesanan='Siap' AND next_status IN ('Selesai','Dibatalkan'))) THEN RAISE EXCEPTION 'RF: Perubahan status tidak valid. Muat ulang pesanan.'; END IF;
      IF next_status='Dibatalkan' AND b.status_pembayaran='Lunas' THEN RAISE EXCEPTION 'RF: Pesanan lunas tidak bisa dibatalkan. Refund belum didukung.'; END IF;
      IF next_status='Selesai' AND b.status_pembayaran<>'Lunas' THEN RAISE EXCEPTION 'RF: Selesaikan pembayaran terlebih dahulu.'; END IF;
      IF next_status='Selesai' AND o.jenis_pesanan='delivery' AND EXISTS(SELECT 1 FROM public.pengiriman WHERE id_pemesanan=oid AND status_pengiriman<>'Terkirim') THEN RAISE EXCEPTION 'RF: Pengiriman harus dikonfirmasi terkirim terlebih dahulu.'; END IF;
      UPDATE public.pemesanan SET status_pemesanan=next_status WHERE id_pemesanan=oid;
      IF next_status='Dibatalkan' THEN
        UPDATE public.pembayaran SET status_pembayaran='Gagal' WHERE id_pembayaran=b.id_pembayaran;
        UPDATE public.pengiriman SET status_pengiriman='Dibatalkan' WHERE id_pemesanan=oid;
      END IF;
    ELSE
      SELECT * INTO g FROM public.pengiriman WHERE id_pemesanan=oid FOR UPDATE;
      IF NOT FOUND OR o.status_pemesanan='Dibatalkan' THEN RAISE EXCEPTION 'RF: Pengiriman tidak tersedia.'; END IF;
      IF p_action='assign_delivery' THEN
        IF r NOT IN ('admin','cashier') THEN RAISE insufficient_privilege; END IF;
        IF g.status_pengiriman NOT IN ('Menunggu','Ditugaskan') THEN RAISE EXCEPTION 'RF: Kurir tidak dapat diganti setelah pengambilan.'; END IF;
        item_id:=(p->>'courier_id')::integer;
        PERFORM 1 FROM public.kurir k JOIN public.staff s USING(id_staff) WHERE k.id_kurir=item_id AND s.is_active;
        IF NOT FOUND THEN RAISE EXCEPTION 'RF: Pilih kurir aktif.'; END IF;
        UPDATE public.pengiriman SET id_kurir=item_id,status_pengiriman='Ditugaskan',waktu_ditugaskan=now() WHERE id_pemesanan=oid;
      ELSE
        IF r NOT IN ('admin','cashier','courier') THEN RAISE insufficient_privilege; END IF;
        next_status:=p->>'status';
        IF NOT ((g.status_pengiriman='Ditugaskan' AND next_status='Diambil') OR (g.status_pengiriman='Diambil' AND next_status='Terkirim')) THEN RAISE EXCEPTION 'RF: Urutan status pengiriman tidak valid.'; END IF;
        IF b.status_pembayaran<>'Lunas' OR o.status_pemesanan NOT IN ('Siap','Selesai') THEN RAISE EXCEPTION 'RF: Pesanan harus siap dan lunas sebelum dikirim.'; END IF;
        UPDATE public.pengiriman SET status_pengiriman=next_status,
          waktu_diambil=CASE WHEN next_status='Diambil' THEN now() ELSE waktu_diambil END,
          waktu_sampai=CASE WHEN next_status='Terkirim' THEN now() ELSE waktu_sampai END WHERE id_pemesanan=oid;
        IF next_status='Terkirim' THEN UPDATE public.pemesanan SET status_pemesanan='Selesai' WHERE id_pemesanan=oid; END IF;
      END IF;
    END IF;

  ELSIF p_action='review' THEN
    IF r NOT IN ('admin','cashier') THEN RAISE insufficient_privilege; END IF;
    IF length(coalesce(p->>'comment',''))>2000 OR (p->>'date')::date>current_date THEN RAISE EXCEPTION 'RF: Tanggal atau komentar ulasan tidak valid.'; END IF;
    INSERT INTO public.ulasan(id_kurir,tanggal_rating,ulasan,rating) VALUES((p->>'courier_id')::integer,(p->>'date')::date,p->>'comment',(p->>'rating')::integer) RETURNING id_ulasan INTO item_id;
    entity:=item_id::text;

  ELSE
    IF r<>'admin' THEN RAISE insufficient_privilege; END IF;
    item_id:=(p->>'id')::integer;
    IF p_action='save_menu' THEN
      IF length(btrim(coalesce(p->>'name',''))) NOT BETWEEN 1 AND 50 OR length(coalesce(p->>'description',''))>2000
        OR length(btrim(coalesce(p->>'category',''))) NOT BETWEEN 1 AND 80 THEN RAISE EXCEPTION 'RF: Nama, kategori, atau deskripsi menu tidak valid.'; END IF;
      IF item_id IS NULL THEN
        INSERT INTO public.menu(nama_menu,deskripsi_menu,harga_satuan,category,is_active)
        VALUES(btrim(p->>'name'),p->>'description',(p->>'price')::integer,btrim(p->>'category'),(p->>'active')::boolean) RETURNING id_menu INTO item_id;
      ELSE
        UPDATE public.menu SET nama_menu=btrim(p->>'name'),deskripsi_menu=p->>'description',harga_satuan=(p->>'price')::integer,
          category=btrim(p->>'category'),is_active=(p->>'active')::boolean WHERE id_menu=item_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'RF: Menu tidak ditemukan.'; END IF;
      END IF;
    ELSIF p_action='save_table' THEN
      IF length(btrim(coalesce(p->>'name',''))) NOT BETWEEN 1 AND 100 OR length(btrim(coalesce(p->>'qr',''))) NOT BETWEEN 1 AND 255 THEN RAISE EXCEPTION 'RF: Nama meja dan kode QR wajib diisi.'; END IF;
      IF item_id IS NULL THEN
        INSERT INTO public.customer(nomor_meja,qr_meja,is_active) VALUES(btrim(p->>'name'),btrim(p->>'qr'),(p->>'active')::boolean) RETURNING id_customer INTO item_id;
      ELSE
        PERFORM 1 FROM public.customer WHERE id_customer=item_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'RF: Meja tidak ditemukan.'; END IF;
        IF NOT (p->>'active')::boolean AND EXISTS(SELECT 1 FROM public.pemesanan WHERE id_customer=item_id AND status_pemesanan NOT IN ('Selesai','Dibatalkan')) THEN RAISE EXCEPTION 'RF: Meja dengan pesanan aktif tidak dapat dinonaktifkan.'; END IF;
        UPDATE public.customer SET nomor_meja=btrim(p->>'name'),qr_meja=btrim(p->>'qr'),is_active=(p->>'active')::boolean WHERE id_customer=item_id;
      END IF;
    ELSIF p_action IN ('save_staff','save_waiter') THEN
      IF length(btrim(coalesce(p->>'first',''))) NOT BETWEEN 1 AND 25 OR length(btrim(coalesce(p->>'last',''))) NOT BETWEEN 1 AND 25 THEN RAISE EXCEPTION 'RF: Nama depan/belakang wajib diisi, maksimal 25 karakter.'; END IF;
      IF p_action='save_staff' THEN
        IF item_id IS NULL THEN
          INSERT INTO public.staff(nama_staff,nama_depan,nama_belakang,is_active) VALUES(btrim(p->>'first')||' '||btrim(p->>'last'),btrim(p->>'first'),btrim(p->>'last'),(p->>'active')::boolean) RETURNING id_staff INTO item_id;
          IF p->>'kind'='kitchen' THEN INSERT INTO public.staff_kitchen VALUES(item_id);
          ELSIF p->>'kind'='courier' THEN INSERT INTO public.kurir(id_staff) VALUES(item_id);
          ELSE RAISE EXCEPTION 'RF: Pilih jenis staf dapur atau kurir.'; END IF;
        ELSE
          UPDATE public.staff SET nama_staff=btrim(p->>'first')||' '||btrim(p->>'last'),nama_depan=btrim(p->>'first'),nama_belakang=btrim(p->>'last'),is_active=(p->>'active')::boolean WHERE id_staff=item_id;
          IF NOT FOUND THEN RAISE EXCEPTION 'RF: Staf tidak ditemukan.'; END IF;
        END IF;
      ELSE
        waiter:=btrim(p->>'waiter_id');
        IF length(coalesce(waiter,'')) NOT BETWEEN 1 AND 10 THEN RAISE EXCEPTION 'RF: ID pelayan wajib diisi, maksimal 10 karakter.'; END IF;
        IF coalesce((p->>'is_new')::boolean,false) THEN
          INSERT INTO public.pelayan VALUES(waiter,btrim(p->>'first'),btrim(p->>'last'),(p->>'active')::boolean);
        ELSE
          UPDATE public.pelayan SET nama_depan=btrim(p->>'first'),nama_belakang=btrim(p->>'last'),is_active=(p->>'active')::boolean WHERE id_pelayan=waiter;
          IF NOT FOUND THEN RAISE EXCEPTION 'RF: Pelayan tidak ditemukan.'; END IF;
        END IF;
        entity:=waiter;
      END IF;
    ELSIF p_action='save_profile' THEN
      rid:=(p->>'user_id')::uuid; new_role:=p->>'role'; staff_id:=(p->>'staff_id')::integer; waiter:=nullif(p->>'waiter_id','');
      IF rid=actor THEN RAISE EXCEPTION 'RF: Perubahan profil sendiri dilakukan admin lain agar akses admin tidak terkunci.'; END IF;
      IF new_role IN ('kitchen','courier') THEN
        PERFORM 1 FROM public.staff WHERE id_staff=staff_id AND is_active;
        IF NOT FOUND THEN RAISE EXCEPTION 'RF: Hubungkan akun ke staf aktif.'; END IF;
        IF new_role='kitchen' AND NOT EXISTS(SELECT 1 FROM public.staff_kitchen WHERE id_staff=staff_id) THEN RAISE EXCEPTION 'RF: Staf bukan anggota dapur.'; END IF;
        IF new_role='courier' AND NOT EXISTS(SELECT 1 FROM public.kurir WHERE id_staff=staff_id) THEN RAISE EXCEPTION 'RF: Staf bukan kurir.'; END IF;
        waiter:=NULL;
      ELSIF new_role='waiter' THEN
        PERFORM 1 FROM public.pelayan WHERE id_pelayan=waiter AND is_active;
        IF NOT FOUND THEN RAISE EXCEPTION 'RF: Hubungkan akun ke pelayan aktif.'; END IF;
        staff_id:=NULL;
      ELSE staff_id:=NULL; waiter:=NULL; END IF;
      INSERT INTO public.profiles(id,display_name,role,id_staff,id_pelayan,is_active)
        VALUES(rid,btrim(p->>'name'),new_role,staff_id,waiter,(p->>'active')::boolean)
        ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,role=excluded.role,id_staff=excluded.id_staff,id_pelayan=excluded.id_pelayan,is_active=excluded.is_active;
      entity:=rid::text;
    ELSIF p_action='settings' THEN
      IF length(btrim(coalesce(p->>'name',''))) NOT BETWEEN 1 AND 100 OR length(coalesce(p->>'footer',''))>500 THEN RAISE EXCEPTION 'RF: Nama restoran atau footer tidak valid.'; END IF;
      UPDATE public.restaurant_settings SET display_name=btrim(p->>'name'),receipt_footer=coalesce(p->>'footer','') WHERE id=1;
      entity:='1';
    ELSE RAISE EXCEPTION 'RF: Tindakan tidak dikenal.';
    END IF;
    entity:=coalesce(entity,item_id::text);
    result:=jsonb_build_object('id',entity);
  END IF;
  INSERT INTO public.audit_log(actor,action,entity_id) VALUES(actor,p_action,entity);
  RETURN result;
EXCEPTION
  WHEN insufficient_privilege THEN RAISE EXCEPTION 'RF: Anda tidak memiliki izin untuk tindakan ini.' USING ERRCODE='42501';
  WHEN unique_violation THEN RAISE EXCEPTION 'RF: ID, meja, kode QR, atau pemetaan akun sudah digunakan. Muat ulang data.';
  WHEN foreign_key_violation THEN RAISE EXCEPTION 'RF: Data terkait tidak ditemukan. Muat ulang pilihan dan coba lagi.';
  WHEN check_violation OR not_null_violation OR invalid_text_representation OR numeric_value_out_of_range THEN
    RAISE EXCEPTION 'RF: Nilai tidak valid. Periksa kelengkapan, jumlah, harga, dan status.';
END $$;
REVOKE ALL ON FUNCTION public.rf_execute(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.rf_execute(text,jsonb) TO authenticated;

CREATE FUNCTION public.rf_table_busy(table_id integer) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,public AS $$
  SELECT public.rf_role() IS NOT NULL AND EXISTS(SELECT 1 FROM public.pemesanan WHERE id_customer=table_id AND status_pemesanan NOT IN ('Selesai','Dibatalkan'));
$$;
REVOKE ALL ON FUNCTION public.rf_table_busy(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.rf_table_busy(integer) TO authenticated;

CREATE FUNCTION public.rf_snapshot(p_from date,p_to date) RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
DECLARE result jsonb; actor_profile jsonb; order_data jsonb; order_count bigint; stats jsonb;
BEGIN
  SELECT to_jsonb(p) INTO actor_profile FROM public.profiles p WHERE id=auth.uid() AND is_active;
  IF actor_profile IS NULL OR public.rf_role() IS NULL THEN RAISE EXCEPTION 'RF: Akun atau staf terkait belum memiliki profil aktif. Hubungi admin.'; END IF;
  IF p_from IS NULL OR p_to IS NULL OR p_to<p_from OR p_to-p_from>366 THEN RAISE EXCEPTION 'RF: Rentang tanggal maksimal 366 hari.'; END IF;
  SELECT count(*) INTO order_count FROM public.pemesanan WHERE created_at>=p_from::timestamp AT TIME ZONE 'Asia/Jakarta' AND created_at<(p_to+1)::timestamp AT TIME ZONE 'Asia/Jakarta';
  SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.id DESC),'[]') INTO order_data FROM (
    SELECT o.id_pemesanan AS id,o.id_customer AS table_id,c.nomor_meja AS table_name,o.jenis_pesanan AS type,
      o.status_pemesanan AS status,o.deskripsi_pesanan AS notes,o.created_at AS time,
      b.total_harga AS total,b.status_pembayaran AS payment_status,b.pay_method AS method,b.amount_received,
      n.nomor_antrian AS queue,g.id_kurir AS courier_id,g.alamat_pengiriman AS address,g.status_pengiriman AS delivery_status,
      g.waktu_ditugaskan,g.waktu_diambil,g.waktu_sampai,
      coalesce((SELECT jsonb_agg(jsonb_build_object('menu_id',d.id_menu,'name',m.nama_menu,'quantity',d.jumlah_item,
        'price',coalesce(d.harga_satuan_saat_transaksi,m.harga_satuan),'reconstructed',d.harga_satuan_saat_transaksi IS NULL) ORDER BY d.id_menu)
        FROM public.detail_pemesanan d JOIN public.menu m USING(id_menu) WHERE d.id_pemesanan=o.id_pemesanan),'[]') AS items,
      (SELECT jsonb_agg(to_jsonb(a)) FROM public.diproses_oleh a WHERE a.id_pemesanan=o.id_pemesanan) AS assignments
    FROM public.pemesanan o LEFT JOIN public.customer c USING(id_customer)
    LEFT JOIN public.pembayaran b USING(id_pembayaran) LEFT JOIN public.nota n USING(id_pembayaran)
    LEFT JOIN public.pengiriman g USING(id_pemesanan)
    WHERE o.created_at>=p_from::timestamp AT TIME ZONE 'Asia/Jakarta' AND o.created_at<(p_to+1)::timestamp AT TIME ZONE 'Asia/Jakarta'
    ORDER BY o.id_pemesanan DESC LIMIT 2000
  ) x;
  SELECT jsonb_build_object('revenue',coalesce(sum(b.total_harga) FILTER(WHERE b.status_pembayaran='Lunas' AND o.status_pemesanan<>'Dibatalkan'),0),
    'orders',count(*),'active',count(*) FILTER(WHERE o.status_pemesanan NOT IN ('Selesai','Dibatalkan')),
    'pending',count(*) FILTER(WHERE b.status_pembayaran='Pending')) INTO stats
    FROM public.pemesanan o LEFT JOIN public.pembayaran b USING(id_pembayaran)
    WHERE o.created_at>=p_from::timestamp AT TIME ZONE 'Asia/Jakarta' AND o.created_at<(p_to+1)::timestamp AT TIME ZONE 'Asia/Jakarta';
  stats:=stats||jsonb_build_object('previous_revenue',coalesce((SELECT sum(b.total_harga)
    FROM public.pemesanan o JOIN public.pembayaran b USING(id_pembayaran)
    WHERE b.status_pembayaran='Lunas' AND o.status_pemesanan<>'Dibatalkan'
      AND o.created_at>=(p_from-(p_to-p_from+1))::timestamp AT TIME ZONE 'Asia/Jakarta'
      AND o.created_at<p_from::timestamp AT TIME ZONE 'Asia/Jakarta'),0));
  result:=jsonb_build_object('profile',actor_profile,'orders',order_data,'order_count',order_count,'stats',stats,
    'menu',coalesce((SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id_menu) FROM public.menu m),'[]'),
    'tables',coalesce((SELECT jsonb_agg(to_jsonb(c)||jsonb_build_object('occupied',public.rf_table_busy(c.id_customer)) ORDER BY c.id_customer) FROM public.customer c),'[]'),
    'staff',coalesce((SELECT jsonb_agg(to_jsonb(s)||jsonb_build_object('kind',CASE WHEN EXISTS(SELECT 1 FROM public.staff_kitchen k WHERE k.id_staff=s.id_staff) THEN 'kitchen' ELSE 'courier' END) ORDER BY s.id_staff) FROM public.staff s),'[]'),
    'kitchen',coalesce((SELECT jsonb_agg(to_jsonb(k)) FROM public.staff_kitchen k),'[]'),
    'couriers',coalesce((SELECT jsonb_agg(to_jsonb(k)) FROM public.kurir k),'[]'),
    'waiters',coalesce((SELECT jsonb_agg(to_jsonb(w) ORDER BY w.id_pelayan) FROM public.pelayan w),'[]'),
    'profiles',coalesce((SELECT jsonb_agg(to_jsonb(p)) FROM public.profiles p),'[]'),
    'reviews',coalesce((SELECT jsonb_agg(to_jsonb(u) ORDER BY u.id_ulasan DESC) FROM public.ulasan u WHERE tanggal_rating BETWEEN p_from AND p_to),'[]'),
    'settings',(SELECT to_jsonb(s) FROM public.restaurant_settings s WHERE id=1));
  RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.rf_snapshot(date,date) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.rf_snapshot(date,date) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
