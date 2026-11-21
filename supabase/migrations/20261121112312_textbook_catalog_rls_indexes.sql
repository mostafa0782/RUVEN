create index if not exists textbook_catalog_uploaded_by_idx on public.textbook_catalog (uploaded_by);
create index if not exists textbook_catalog_reviewed_by_idx on public.textbook_catalog (reviewed_by);
alter table public.textbook_catalog drop constraint if exists textbook_catalog_reviewed_by_fkey;
alter table public.textbook_catalog add constraint textbook_catalog_reviewed_by_fkey
  foreign key (reviewed_by) references public.profiles(id) on delete restrict;
drop policy if exists "textbook catalog approved or admin read" on public.textbook_catalog;
create policy "textbook catalog approved or admin read"
  on public.textbook_catalog for select to authenticated
  using (
    (select private.is_school_admin())
    or (status = 'approved' and (class_name is null or class_name = (select private.current_school_class())))
  );
drop policy if exists "textbook catalog admin insert" on public.textbook_catalog;
create policy "textbook catalog admin insert"
  on public.textbook_catalog for insert to authenticated
  with check ((select private.is_school_admin()) and uploaded_by = (select auth.uid()));
drop policy if exists "textbook catalog admin update" on public.textbook_catalog;
create policy "textbook catalog admin update"
  on public.textbook_catalog for update to authenticated
  using ((select private.is_school_admin()))
  with check ((select private.is_school_admin()));
drop policy if exists "textbook catalog admin delete" on public.textbook_catalog;
create policy "textbook catalog admin delete"
  on public.textbook_catalog for delete to authenticated
  using ((select private.is_school_admin()));
drop policy if exists "ruven textbook approved file read" on storage.objects;
create policy "ruven textbook approved file read"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'ruven-school-files'
    and exists (
      select 1 from public.textbook_catalog tc
      where tc.storage_path = storage.objects.name
        and tc.status = 'approved'
        and (tc.class_name is null or tc.class_name = (select private.current_school_class()))
    )
  );
drop trigger if exists textbook_catalog_touch_updated_at on public.textbook_catalog;
create trigger textbook_catalog_touch_updated_at
  before update on public.textbook_catalog
  for each row execute function public.touch_updated_at();
