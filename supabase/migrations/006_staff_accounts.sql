BEGIN;

CREATE FUNCTION restoflow_private.rf_split_staff_name(full_name text, OUT first_name text, OUT last_name text)
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE
  cleaned text := regexp_replace(btrim(coalesce(full_name,'')), '\s+', ' ', 'g');
  space_pos integer := strpos(cleaned, ' ');
BEGIN
  IF space_pos = 0 THEN
    first_name := left(cleaned, 25);
    last_name := '-';
  ELSE
    first_name := left(btrim(substr(cleaned, 1, space_pos - 1)), 25);
    last_name := left(btrim(substr(cleaned, space_pos + 1)), 25);
    IF last_name = '' THEN last_name := '-'; END IF;
  END IF;
END $$;

CREATE FUNCTION restoflow_private.rf_validate_staff_account(actor uuid, p jsonb) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE
  new_role text := p->>'role';
  staff_id integer := CASE WHEN jsonb_typeof(p->'staff_id')='number' OR (p->>'staff_id') ~ '^[0-9]+$' THEN (p->>'staff_id')::integer END;
  waiter_id text := nullif(btrim(coalesce(p->>'waiter_id','')),'');
  create_identity boolean := coalesce((p->>'create_identity')::boolean, false);
  first_name text;
  last_name text;
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
    IF create_identity AND staff_id IS NULL THEN
      SELECT * INTO first_name, last_name FROM restoflow_private.rf_split_staff_name(p->>'name');
      IF length(first_name) NOT BETWEEN 1 AND 25 OR length(last_name) NOT BETWEEN 1 AND 25
          OR length(first_name || ' ' || last_name) > 50 THEN
        RAISE EXCEPTION 'RF: Nama staf baru maksimal 50 karakter.';
      END IF;
    ELSE
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
    END IF;
  ELSIF new_role='waiter' THEN
    IF waiter_id IS NULL OR length(waiter_id) NOT BETWEEN 1 AND 10 THEN
      RAISE EXCEPTION 'RF: Hubungkan akun ke pelayan aktif.';
    END IF;
    IF create_identity AND NOT EXISTS(SELECT 1 FROM public.pelayan WHERE id_pelayan=waiter_id) THEN
      SELECT * INTO first_name, last_name FROM restoflow_private.rf_split_staff_name(p->>'name');
      IF length(first_name) NOT BETWEEN 1 AND 25 OR length(last_name) NOT BETWEEN 1 AND 25 THEN
        RAISE EXCEPTION 'RF: Nama pelayan baru tidak valid.';
      END IF;
    ELSE
      PERFORM 1 FROM public.pelayan WHERE id_pelayan=waiter_id AND is_active FOR SHARE;
      IF NOT FOUND THEN RAISE EXCEPTION 'RF: Hubungkan akun ke pelayan aktif.'; END IF;
      IF EXISTS(SELECT 1 FROM public.profiles WHERE id_pelayan=waiter_id) THEN
        RAISE EXCEPTION 'RF: Identitas pelayan sudah terhubung ke akun.';
      END IF;
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
  new_role text;
  resolved_staff_id integer;
  resolved_waiter_id text;
  create_identity boolean;
  first_name text;
  last_name text;
  full_staff_name text;
BEGIN
  IF p IS NULL THEN RETURN NEW; END IF;
  IF TG_OP='UPDATE' AND p IS NOT DISTINCT FROM OLD.raw_app_meta_data->'restoflow_staff_account' THEN
    RETURN NEW;
  END IF;
  actor := (p->>'actor')::uuid;
  PERFORM restoflow_private.rf_validate_staff_account(actor,p);
  new_role := p->>'role';
  create_identity := coalesce((p->>'create_identity')::boolean, false);
  IF new_role IN ('kitchen','courier') THEN
    resolved_staff_id := CASE WHEN jsonb_typeof(p->'staff_id')='number' OR (p->>'staff_id') ~ '^[0-9]+$' THEN (p->>'staff_id')::integer END;
    IF create_identity AND resolved_staff_id IS NULL THEN
      SELECT * INTO first_name, last_name FROM restoflow_private.rf_split_staff_name(p->>'name');
      full_staff_name := CASE WHEN last_name='-' THEN first_name ELSE left(first_name || ' ' || last_name, 50) END;
      INSERT INTO public.staff(nama_staff,nama_depan,nama_belakang,is_active)
      VALUES(full_staff_name,first_name,last_name,true) RETURNING id_staff INTO resolved_staff_id;
      IF new_role='kitchen' THEN
        INSERT INTO public.staff_kitchen(id_staff) VALUES(resolved_staff_id);
      ELSE
        INSERT INTO public.kurir(id_staff) VALUES(resolved_staff_id);
      END IF;
    END IF;
  ELSIF new_role='waiter' THEN
    resolved_waiter_id := nullif(btrim(coalesce(p->>'waiter_id','')),'');
    IF create_identity AND NOT EXISTS(SELECT 1 FROM public.pelayan WHERE id_pelayan=resolved_waiter_id) THEN
      SELECT * INTO first_name, last_name FROM restoflow_private.rf_split_staff_name(p->>'name');
      INSERT INTO public.pelayan(id_pelayan,nama_depan,nama_belakang,is_active)
      VALUES(resolved_waiter_id,first_name,last_name,true);
    END IF;
  END IF;
  INSERT INTO public.profiles(id,display_name,role,id_staff,id_pelayan)
  VALUES(NEW.id,btrim(p->>'name'),new_role,resolved_staff_id,resolved_waiter_id);
  INSERT INTO public.audit_log(actor,action,entity_id) VALUES(actor,'create_staff_account',NEW.id::text);
  RETURN NEW;
END $$;

CREATE TRIGGER rf_staff_account_profile
AFTER INSERT OR UPDATE OF raw_app_meta_data ON auth.users
FOR EACH ROW EXECUTE FUNCTION restoflow_private.rf_create_staff_profile();

REVOKE ALL ON FUNCTION restoflow_private.rf_split_staff_name(text),
  restoflow_private.rf_validate_staff_account(uuid,jsonb),
  restoflow_private.rf_create_staff_profile() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION restoflow_private.rf_check_staff_account(jsonb),
  public.rf_check_staff_account(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION restoflow_private.rf_check_staff_account(jsonb),
  public.rf_check_staff_account(jsonb) TO authenticated;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='supabase_auth_admin') THEN
    GRANT USAGE ON SCHEMA restoflow_private TO supabase_auth_admin;
    GRANT EXECUTE ON FUNCTION restoflow_private.rf_create_staff_profile() TO supabase_auth_admin;
  END IF;
END $$;
NOTIFY pgrst,'reload schema';
COMMIT;
