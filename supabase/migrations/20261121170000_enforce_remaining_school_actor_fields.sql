-- Derive actor and timestamps for the remaining school-owned records.
-- Keep audit identity fields immutable after creation.

CREATE OR REPLACE FUNCTION private.enforce_school_item_actor()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authenticated school content actor is required';
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.created_by := auth.uid();
    NEW.created_at := now();
    NEW.updated_at := now();
    RETURN NEW;
  END IF;

  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.item_type IS DISTINCT FROM OLD.item_type
     OR NEW.created_by IS DISTINCT FROM OLD.created_by
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'school item identity fields are immutable';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS school_items_enforce_actor ON public.school_items;
CREATE TRIGGER school_items_enforce_actor
BEFORE INSERT OR UPDATE ON public.school_items
FOR EACH ROW
EXECUTE FUNCTION private.enforce_school_item_actor();


CREATE OR REPLACE FUNCTION private.enforce_report_update_actor()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authenticated report update actor is required';
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.updated_by := auth.uid();
    NEW.created_at := now();
    RETURN NEW;
  END IF;

  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.report_id IS DISTINCT FROM OLD.report_id
     OR NEW.updated_by IS DISTINCT FROM OLD.updated_by
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'report update identity fields are immutable';
  END IF;

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS report_updates_enforce_actor ON public.report_updates;
CREATE TRIGGER report_updates_enforce_actor
BEFORE INSERT OR UPDATE ON public.report_updates
FOR EACH ROW
EXECUTE FUNCTION private.enforce_report_update_actor();


CREATE OR REPLACE FUNCTION private.enforce_student_roster_actor()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authenticated roster actor is required';
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.created_by := auth.uid();
    NEW.created_at := now();
    NEW.updated_at := now();
    RETURN NEW;
  END IF;

  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.created_by IS DISTINCT FROM OLD.created_by
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'student roster identity fields are immutable';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS student_roster_enforce_actor ON public.student_roster;
CREATE TRIGGER student_roster_enforce_actor
BEFORE INSERT OR UPDATE ON public.student_roster
FOR EACH ROW
EXECUTE FUNCTION private.enforce_student_roster_actor();
