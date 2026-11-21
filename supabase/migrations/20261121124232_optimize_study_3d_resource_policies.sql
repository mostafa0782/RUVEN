drop policy if exists "Admins manage 3D study resources" on public.study_3d_resources;
drop policy if exists "Students read approved 3D study resources" on public.study_3d_resources;

create policy "Admins and students read 3D study resources"
on public.study_3d_resources
for select to authenticated
using (
  (select private.is_school_admin())
  or (
    status = 'approved'
    and (select private.current_school_role())::text = 'student'
  )
);

create policy "Admins insert 3D study resources"
on public.study_3d_resources
for insert to authenticated
with check ((select private.is_school_admin()));

create policy "Admins update 3D study resources"
on public.study_3d_resources
for update to authenticated
using ((select private.is_school_admin()))
with check ((select private.is_school_admin()));

create policy "Admins delete 3D study resources"
on public.study_3d_resources
for delete to authenticated
using ((select private.is_school_admin()));
