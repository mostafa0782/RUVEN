-- RUVEN security hardening: keep profile data private and RPC helpers unavailable to anon.
drop policy if exists "authenticated users read profiles" on public.profiles;
create policy "users read own profile or admins read all"
on public.profiles for select to authenticated
using (id = (select auth.uid()) or (select public.is_school_admin()));

drop policy if exists "users create own student profile" on public.profiles;
revoke insert on public.profiles from authenticated;

drop policy if exists "users update own profile name" on public.profiles;
create policy "users update own profile name"
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

-- These helper functions are needed by RLS policies for signed-in users, but not anonymous clients.
revoke all on function public.current_school_role() from public, anon;
revoke all on function public.is_school_admin() from public, anon;
revoke all on function public.is_school_owner() from public, anon;
revoke all on function public.teacher_has_assignment(text, text) from public, anon;
grant execute on function public.current_school_role() to authenticated;
grant execute on function public.is_school_admin() to authenticated;
grant execute on function public.is_school_owner() to authenticated;
grant execute on function public.teacher_has_assignment(text, text) to authenticated;

-- This event-trigger function is an internal database hook, not a client API.
revoke all on function public.rls_auto_enable() from public, anon, authenticated;

create index if not exists report_updates_report_id_idx on public.report_updates(report_id);
create index if not exists report_updates_updated_by_idx on public.report_updates(updated_by);
