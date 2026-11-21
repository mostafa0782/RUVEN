insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ruven-school-files', 'ruven-school-files', false, 20971520, array['application/pdf'])
on conflict (id) do update
set public = false,
    file_size_limit = 20971520,
    allowed_mime_types = array['application/pdf'];

drop policy if exists "ruven textbook admin upload" on storage.objects;
create policy "ruven textbook admin upload"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'ruven-school-files'
    and name like 'textbooks/%'
    and (select private.is_school_admin())
  );

drop policy if exists "textbook catalog admin update" on public.textbook_catalog;
create policy "textbook catalog admin update"
  on public.textbook_catalog for update to authenticated
  using ((select private.is_school_admin()))
  with check (
    (select private.is_school_admin())
    and (
      (status = 'pending' and reviewed_by is null and reviewed_at is null)
      or
      (status in ('approved','rejected')
        and reviewed_by = (select auth.uid())
        and reviewed_at is not null)
    )
  );
