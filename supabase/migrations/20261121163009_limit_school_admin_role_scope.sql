-- Educational counselors are not school administrators. Their
-- dedicated reporting/support permissions must be implemented separately;
-- they must not inherit broad access to all profiles, leave requests,
-- study records, decisions, catalogues, and school operations.
CREATE OR REPLACE FUNCTION private.is_school_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
  select coalesce((
    select (
      p.is_owner
      or p.role::text in ('owner', 'principal', 'assistant_principal')
    )
    from public.profiles p
    where p.id = (select auth.uid())
  ), false)
$function$;
