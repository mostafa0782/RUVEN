-- Student roster contains personal data. Class monitors must not receive a school-wide
-- directory through direct table access; use a future narrowly scoped review flow instead.
drop policy if exists "Staff can read roster for review" on public.student_roster;
create policy "School administrators can read roster for review"
on public.student_roster
for select
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.role in ('assistant_principal', 'principal', 'owner')
  )
);
