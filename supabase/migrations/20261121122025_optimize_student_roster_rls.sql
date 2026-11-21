-- Consolidate roster SELECT authorization and cache auth.uid() per statement.
drop policy if exists "Owner manages student roster" on public.student_roster;
drop policy if exists "School administrators can read roster for review" on public.student_roster;

create policy "Authorized administrators read student roster"
on public.student_roster
for select
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and (
        p.is_owner = true
        or p.role in ('assistant_principal', 'principal', 'owner')
      )
  )
);

create policy "Owner inserts student roster"
on public.student_roster
for insert
to authenticated
with check (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.is_owner = true
  )
);

create policy "Owner updates student roster"
on public.student_roster
for update
to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.is_owner = true
  )
)
with check (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.is_owner = true
  )
);

create policy "Owner deletes student roster"
on public.student_roster
for delete
to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.is_owner = true
  )
);
