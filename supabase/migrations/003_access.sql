BEGIN;
CREATE FUNCTION public.rf_role() RETURNS text LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public AS $$
  SELECT p.role FROM public.profiles p WHERE p.id=auth.uid() AND p.is_active
    AND (p.id_staff IS NULL OR EXISTS(SELECT 1 FROM public.staff s WHERE s.id_staff=p.id_staff AND s.is_active))
    AND (p.id_pelayan IS NULL OR EXISTS(SELECT 1 FROM public.pelayan w WHERE w.id_pelayan=p.id_pelayan AND w.is_active));
$$;
CREATE FUNCTION public.rf_can_order(order_id integer) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public AS $$
  SELECT CASE
    WHEN public.rf_role() IN ('admin','cashier') THEN true
    WHEN public.rf_role()='kitchen' THEN EXISTS (SELECT 1 FROM public.pemesanan WHERE id_pemesanan=order_id AND status_pemesanan IN ('Menunggu','Diproses','Siap'))
    WHEN public.rf_role()='waiter' THEN EXISTS (
      SELECT 1 FROM public.pemesanan o WHERE o.id_pemesanan=order_id AND (o.created_by=auth.uid() OR EXISTS (
        SELECT 1 FROM public.diproses_oleh d JOIN public.profiles p ON p.id_pelayan=d.id_pelayan
        WHERE d.id_pemesanan=o.id_pemesanan AND p.id=auth.uid() AND p.is_active)))
    WHEN public.rf_role()='courier' THEN EXISTS (
      SELECT 1 FROM public.pengiriman g JOIN public.kurir k USING(id_kurir)
      JOIN public.profiles p ON p.id_staff=k.id_staff
      WHERE g.id_pemesanan=order_id AND p.id=auth.uid() AND p.is_active)
    ELSE false END;
$$;
DO $$ DECLARE t text; BEGIN
  FOREACH t IN ARRAY ARRAY['customer','menu','staff','staff_kitchen','kurir','ulasan','pelayan','pembayaran','pemesanan','detail_pemesanan','diproses_oleh','nota','profiles','pengiriman','restaurant_settings','audit_log'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated', t);
    EXECUTE format('GRANT SELECT ON public.%I TO authenticated', t);
  END LOOP;
END $$;
CREATE POLICY profile_read ON public.profiles FOR SELECT TO authenticated USING(id=auth.uid() OR public.rf_role()='admin');
CREATE POLICY menu_read ON public.menu FOR SELECT TO authenticated USING(public.rf_role() IS NOT NULL);
CREATE POLICY table_read ON public.customer FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier','waiter'));
CREATE POLICY staff_read ON public.staff FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier','waiter') OR id_staff=(SELECT id_staff FROM public.profiles WHERE id=auth.uid()));
CREATE POLICY kitchen_read ON public.staff_kitchen FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier','waiter','kitchen'));
CREATE POLICY courier_read ON public.kurir FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier') OR id_staff=(SELECT id_staff FROM public.profiles WHERE id=auth.uid()));
CREATE POLICY waiter_read ON public.pelayan FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier','waiter'));
CREATE POLICY order_read ON public.pemesanan FOR SELECT TO authenticated USING(public.rf_can_order(id_pemesanan));
CREATE POLICY detail_read ON public.detail_pemesanan FOR SELECT TO authenticated USING(public.rf_can_order(id_pemesanan));
CREATE POLICY assignment_read ON public.diproses_oleh FOR SELECT TO authenticated USING(public.rf_can_order(id_pemesanan));
CREATE POLICY payment_read ON public.pembayaran FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier'));
CREATE POLICY receipt_read ON public.nota FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier'));
CREATE POLICY delivery_read ON public.pengiriman FOR SELECT TO authenticated USING(public.rf_can_order(id_pemesanan));
CREATE POLICY review_read ON public.ulasan FOR SELECT TO authenticated USING(public.rf_role() IN ('admin','cashier') OR id_kurir IN (SELECT id_kurir FROM public.kurir WHERE id_staff=(SELECT id_staff FROM public.profiles WHERE id=auth.uid())));
CREATE POLICY settings_read ON public.restaurant_settings FOR SELECT TO authenticated USING(public.rf_role() IS NOT NULL);
CREATE POLICY audit_read ON public.audit_log FOR SELECT TO authenticated USING(public.rf_role()='admin');
REVOKE ALL ON FUNCTION public.rf_role(), public.rf_can_order(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rf_role(), public.rf_can_order(integer) TO authenticated;
COMMIT;
