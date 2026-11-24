BEGIN;

CREATE FUNCTION restoflow_private.rf_validate_staff_account(actor uuid, p jsonb) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE
  new_role text := p->>'role';
  staff_id integer := (p->>'staff_id')::integer;
  waiter_id text := nullif(p->>'waiter_id','');
BEGIN
  PERFORM 1 FROM public.profiles WHERE id=actor AND role='admin' AND is_active FOR SHARE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'RF: Hanya admin aktif yang dapat membuat akun.' USING ERRCODE='42501';
  END IF;
  IF coalesce(new_role,'') NOT IN ('admin','cashier','kitchen','waiter','courier')
      OR length(btrim(coalesce(p->>'name',''))) NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'RF: Nama atau peran akun tidak valid.';
  END IF;
  IF new_role IN ('kitchen','courier') THEN
    PERFORM 1 FROM public.staff WHERE id_staff=staff_id AND is_active FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'RF: Hubungkan akun ke staf aktif.'; END IF;
    IF new_role='kitchen' AND NOT EXISTS(SELECT 1 FROM public.staff_kitchen WHERE id_staff=staff_id) THEN
      RAISE EXCEPTION 'RF: Staf bukan anggota dapur.';
    END IF;
    IF new_role='courier' AND NOT EXISTS(SELECT 1 FROM public.kurir WHERE id_staff=staff_id) THEN
      RAISE EXCEPTION 'RF: Staf bukan kurir.';
    END IF;
    IF EXISTS(SELECT 1 FROM public.profiles WHERE id_staff=staff_id) THEN
      RAISE EXCEPTION 'RF: Identitas staf sudah terhubung ke akun.';
    END IF;
  ELSIF new_role='waiter' THEN
    PERFORM 1 FROM public.pelayan WHERE id_pelayan=waiter_id AND is_active FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'RF: Hubungkan akun ke pelayan aktif.'; END IF;
    IF EXISTS(SELECT 1 FROM public.profiles WHERE id_pelayan=waiter_id) THEN
      RAISE EXCEPTION 'RF: Identitas pelayan sudah terhubung ke akun.';
    END IF;
  END IF;
END $$;

CREATE FUNCTION restoflow_private.rf_check_staff_account(p jsonb) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
  PERFORM restoflow_private.rf_validate_staff_account(auth.uid(),p);
  RETURN true;
END $$;

CREATE FUNCTION public.rf_check_staff_account(p jsonb) RETURNS boolean
LANGUAGE sql SECURITY INVOKER SET search_path=pg_catalog AS $$
  SELECT restoflow_private.rf_check_staff_account(p);
$$;

-- Auth Admin menulis app_metadata dalam transaksi pembuatan user. Metadata pengguna biasa diabaikan.
CREATE FUNCTION restoflow_private.rf_create_staff_profile() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE
  p jsonb := NEW.raw_app_meta_data->'restoflow_staff_account';
  actor uuid;
BEGIN
  IF p IS NULL THEN RETURN NEW; END IF;
  IF TG_OP='UPDATE' AND p IS NOT DISTINCT FROM OLD.raw_app_meta_data->'restoflow_staff_account' THEN
    RETURN NEW;
  END IF;
  actor := (p->>'actor')::uuid;
  PERFORM restoflow_private.rf_validate_staff_account(actor,p);
  INSERT INTO public.profiles(id,display_name,role,id_staff,id_pelayan)
  VALUES(NEW.id,btrim(p->>'name'),p->>'role',
    CASE WHEN p->>'role' IN ('kitchen','courier') THEN (p->>'staff_id')::integer END,
    CASE WHEN p->>'role'='waiter' THEN p->>'waiter_id' END);
  INSERT INTO public.audit_log(actor,action,entity_id) VALUES(actor,'create_staff_account',NEW.id::text);
  RETURN NEW;
END $$;

CREATE TRIGGER rf_staff_account_profile
AFTER INSERT OR UPDATE OF raw_app_meta_data ON auth.users
FOR EACH ROW EXECUTE FUNCTION restoflow_private.rf_create_staff_profile();

REVOKE ALL ON FUNCTION restoflow_private.rf_validate_staff_account(uuid,jsonb),
  restoflow_private.rf_create_staff_profile() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION restoflow_private.rf_check_staff_account(jsonb),
  public.rf_check_staff_account(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION restoflow_private.rf_check_staff_account(jsonb),
  public.rf_check_staff_account(jsonb) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
