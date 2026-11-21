drop policy if exists "ruven school file upload roles" on storage.objects;
create policy "ruven school file upload roles" on storage.objects for insert to authenticated
with check (bucket_id = 'ruven-school-files' and (select auth.uid()) is not null
  and (private.is_school_admin() or private.current_school_role()::text in ('teacher','class_monitor')));

drop policy if exists "ruven school file read published or owner" on storage.objects;
create policy "ruven school file read published or owner" on storage.objects for select to authenticated
using (bucket_id = 'ruven-school-files' and (
  owner_id = (select auth.uid())::text or private.is_school_admin()
  or exists (select 1 from public.school_items si where si.attachment_path = name and si.published = true
    and (si.class_name is null or si.class_name = private.current_school_class()))
));

drop policy if exists "ruven school file delete owner or admin" on storage.objects;
create policy "ruven school file delete owner or admin" on storage.objects for delete to authenticated
using (bucket_id = 'ruven-school-files' and (owner_id = (select auth.uid())::text or private.is_school_admin()));
