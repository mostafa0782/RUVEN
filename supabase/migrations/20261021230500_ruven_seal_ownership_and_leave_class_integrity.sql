drop policy if exists "admins manage seals" on public.school_seals;
create policy "admins insert own seals" on public.school_seals
for insert to authenticated
with check (public.is_school_admin() and owner_id = (select auth.uid()));
create policy "admins update own seals" on public.school_seals
for update to authenticated
using (public.is_school_admin() and owner_id = (select auth.uid()))
with check (public.is_school_admin() and owner_id = (select auth.uid()));
create policy "admins delete own seals" on public.school_seals
for delete to authenticated
using (public.is_school_admin() and owner_id = (select auth.uid()));

drop policy if exists "students submit own leave" on public.leave_requests;
create policy "students submit own leave" on public.leave_requests
for insert to authenticated
with check (
  student_id = (select auth.uid())
  and (select public.current_school_role()::text) = 'student'
  and class_name = (select public.current_school_class())
  and status = 'pending'
  and reviewed_by is null
  and decision_seal_id is null
);
