create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create or replace function private.current_school_class()
returns text language sql stable security definer set search_path = ''
as $$ select p.class_name from public.profiles p where p.id = (select auth.uid()) $$;

create or replace function private.current_school_role()
returns public.school_role language sql stable security definer set search_path = ''
as $$ select p.role from public.profiles p where p.id = (select auth.uid()) $$;

create or replace function private.is_school_admin()
returns boolean language sql stable security definer set search_path = ''
as $$ select coalesce((select (p.is_owner or p.role::text in ('owner','principal','assistant_principal','educational_counselor')) from public.profiles p where p.id = (select auth.uid())), false) $$;

create or replace function private.is_school_owner()
returns boolean language sql stable security definer set search_path = ''
as $$ select coalesce((select (p.is_owner or p.role::text = 'owner') from public.profiles p where p.id = (select auth.uid())), false) $$;

create or replace function private.teacher_has_assignment(subject text, class_value text)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (select 1 from public.teacher_subjects ts where ts.teacher_id = (select auth.uid()) and ts.subject_name = subject and ts.class_name = class_value) $$;

revoke all on function private.current_school_class() from public, anon;
revoke all on function private.current_school_role() from public, anon;
revoke all on function private.is_school_admin() from public, anon;
revoke all on function private.is_school_owner() from public, anon;
revoke all on function private.teacher_has_assignment(text,text) from public, anon;
grant execute on function private.current_school_class() to authenticated;
grant execute on function private.current_school_role() to authenticated;
grant execute on function private.is_school_admin() to authenticated;
grant execute on function private.is_school_owner() to authenticated;
grant execute on function private.teacher_has_assignment(text,text) to authenticated;

do $$
declare p record; q text; wc text; stmt text;
begin
  for p in select schemaname, tablename, policyname, cmd, qual, with_check from pg_policies
    where schemaname in ('public','storage') and (
      coalesce(qual,'') ~ '(current_school_class|current_school_role|is_school_admin|is_school_owner|teacher_has_assignment)\s*\('
      or coalesce(with_check,'') ~ '(current_school_class|current_school_role|is_school_admin|is_school_owner|teacher_has_assignment)\s*\(')
  loop
    q := p.qual; wc := p.with_check;
    if q is not null then
      q := regexp_replace(q, '(^|[^.[:alnum:]_])current_school_class\s*\(\)', '\1private.current_school_class()', 'g');
      q := regexp_replace(q, '(^|[^.[:alnum:]_])current_school_role\s*\(\)', '\1private.current_school_role()', 'g');
      q := regexp_replace(q, '(^|[^.[:alnum:]_])is_school_admin\s*\(\)', '\1private.is_school_admin()', 'g');
      q := regexp_replace(q, '(^|[^.[:alnum:]_])is_school_owner\s*\(\)', '\1private.is_school_owner()', 'g');
      q := regexp_replace(q, '(^|[^.[:alnum:]_])teacher_has_assignment\s*\(', '\1private.teacher_has_assignment(', 'g');
    end if;
    if wc is not null then
      wc := regexp_replace(wc, '(^|[^.[:alnum:]_])current_school_class\s*\(\)', '\1private.current_school_class()', 'g');
      wc := regexp_replace(wc, '(^|[^.[:alnum:]_])current_school_role\s*\(\)', '\1private.current_school_role()', 'g');
      wc := regexp_replace(wc, '(^|[^.[:alnum:]_])is_school_admin\s*\(\)', '\1private.is_school_admin()', 'g');
      wc := regexp_replace(wc, '(^|[^.[:alnum:]_])is_school_owner\s*\(\)', '\1private.is_school_owner()', 'g');
      wc := regexp_replace(wc, '(^|[^.[:alnum:]_])teacher_has_assignment\s*\(', '\1private.teacher_has_assignment(', 'g');
    end if;
    stmt := format('alter policy %I on %I.%I', p.policyname, p.schemaname, p.tablename);
    if p.cmd in ('SELECT','UPDATE','DELETE','ALL') and q is not null then stmt := stmt || format(' using (%s)', q); end if;
    if p.cmd in ('INSERT','UPDATE','ALL') and wc is not null then stmt := stmt || format(' with check (%s)', wc); end if;
    execute stmt;
  end loop;
end $$;

revoke execute on function public.current_school_class() from public, anon, authenticated;
revoke execute on function public.current_school_role() from public, anon, authenticated;
revoke execute on function public.is_school_admin() from public, anon, authenticated;
revoke execute on function public.is_school_owner() from public, anon, authenticated;
revoke execute on function public.teacher_has_assignment(text,text) from public, anon, authenticated;
