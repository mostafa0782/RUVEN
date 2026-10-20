-- RUVEN owner role: the project owner is above normal school administration.
alter type public.school_role add value if not exists 'owner';

create or replace function public.is_school_owner()
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce((
    select (p.is_owner or p.role::text = 'owner')
    from public.profiles p
    where p.id = (select auth.uid())
  ), false)
$$;

create or replace function public.is_school_admin()
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce((
    select (p.is_owner or p.role::text in ('owner', 'principal', 'assistant_principal'))
    from public.profiles p
    where p.id = (select auth.uid())
  ), false)
$$;

drop policy if exists "owner manages profiles" on public.profiles;
create policy "owner manages profiles"
on public.profiles for all to authenticated
using (public.is_school_owner())
with check (public.is_school_owner());

drop policy if exists "owner manages teacher assignments" on public.teacher_subjects;
create policy "owner manages teacher assignments"
on public.teacher_subjects for all to authenticated
using (public.is_school_owner())
with check (public.is_school_owner());

drop policy if exists "owner manages school items" on public.school_items;
create policy "owner manages school items"
on public.school_items for all to authenticated
using (public.is_school_owner())
with check (public.is_school_owner());

drop policy if exists "owner manages report updates" on public.report_updates;
create policy "owner manages report updates"
on public.report_updates for all to authenticated
using (public.is_school_owner())
with check (public.is_school_owner());

comment on type public.school_role is
'RUVEN roles. owner is the highest-trust role and has full school-data administration privileges.';
