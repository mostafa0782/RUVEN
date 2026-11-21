create or replace function public.is_school_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select coalesce((
    select (p.is_owner or p.role::text in ('owner', 'principal', 'assistant_principal', 'educational_counselor'))
    from public.profiles p
    where p.id = (select auth.uid())
  ), false)
$function$;

drop policy if exists "admins review leave" on public.leave_requests;
create policy "admins review leave" on public.leave_requests
for update to authenticated
using (public.is_school_admin())
with check (
  public.is_school_admin()
  and reviewed_by = (select auth.uid())
  and (
    status <> 'approved'
    or exists (
      select 1 from public.school_seals s
      where s.id = decision_seal_id
        and s.owner_id = (select auth.uid())
        and s.is_active = true
    )
  )
);
