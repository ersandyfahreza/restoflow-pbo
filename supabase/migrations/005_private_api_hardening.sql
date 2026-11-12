BEGIN;
-- Hanya wrapper invoker yang tersedia melalui schema API public.
CREATE SCHEMA IF NOT EXISTS restoflow_private;
REVOKE ALL ON SCHEMA restoflow_private FROM PUBLIC,anon;
GRANT USAGE ON SCHEMA restoflow_private TO authenticated;

ALTER FUNCTION public.rf_role() SET SCHEMA restoflow_private;
ALTER FUNCTION public.rf_can_order(integer) SET SCHEMA restoflow_private;
ALTER FUNCTION public.rf_table_busy(integer) SET SCHEMA restoflow_private;
ALTER FUNCTION public.rf_write_lines(integer,jsonb) SET SCHEMA restoflow_private;
ALTER FUNCTION public.rf_execute(text,jsonb) RENAME TO rf_execute_impl;
ALTER FUNCTION public.rf_execute_impl(text,jsonb) SET SCHEMA restoflow_private;

-- Body SQL/PLpgSQL berisi nama textual; sesuaikan tanpa mengubah OID/dependensi policy.
DO $$ DECLARE fn record; definition text; helper text; BEGIN
  FOR fn IN SELECT p.oid FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE (n.nspname='restoflow_private' AND p.proname IN ('rf_role','rf_can_order','rf_table_busy','rf_write_lines','rf_execute_impl'))
       OR (n.nspname='public' AND p.proname='rf_snapshot') LOOP
    definition:=pg_get_functiondef(fn.oid);
    FOREACH helper IN ARRAY ARRAY['rf_role','rf_can_order','rf_table_busy','rf_write_lines'] LOOP
      definition:=replace(definition,'public.'||helper||'(','restoflow_private.'||helper||'(');
    END LOOP;
    EXECUTE definition;
  END LOOP;
END $$;

CREATE FUNCTION public.rf_execute(p_action text,p jsonb) RETURNS jsonb
LANGUAGE sql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
  SELECT restoflow_private.rf_execute_impl(p_action,p);
$$;
REVOKE ALL ON FUNCTION public.rf_execute(text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.rf_execute(text,jsonb) TO authenticated;

ALTER POLICY profile_read ON public.profiles USING(id=(SELECT auth.uid()) OR (SELECT restoflow_private.rf_role())='admin');
ALTER POLICY staff_read ON public.staff USING((SELECT restoflow_private.rf_role()) IN ('admin','cashier','waiter')
  OR id_staff=(SELECT p.id_staff FROM public.profiles p WHERE p.id=(SELECT auth.uid())));
ALTER POLICY courier_read ON public.kurir USING((SELECT restoflow_private.rf_role()) IN ('admin','cashier')
  OR id_staff=(SELECT p.id_staff FROM public.profiles p WHERE p.id=(SELECT auth.uid())));
ALTER POLICY review_read ON public.ulasan USING((SELECT restoflow_private.rf_role()) IN ('admin','cashier')
  OR id_kurir IN (SELECT k.id_kurir FROM public.kurir k WHERE k.id_staff=(SELECT p.id_staff FROM public.profiles p WHERE p.id=(SELECT auth.uid()))));
CREATE INDEX order_creator_idx ON public.pemesanan(created_by);
CREATE INDEX audit_actor_idx ON public.audit_log(actor);
NOTIFY pgrst,'reload schema';
COMMIT;
