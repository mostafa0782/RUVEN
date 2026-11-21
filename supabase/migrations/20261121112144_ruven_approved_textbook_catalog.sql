create table if not exists public.textbook_catalog (
  id uuid primary key default gen_random_uuid(),
  grade_name text not null check (char_length(btrim(grade_name)) between 1 and 80),
  class_name text check (class_name is null or char_length(btrim(class_name)) between 1 and 80),
  subject_name text not null check (char_length(btrim(subject_name)) between 1 and 80),
  title text not null check (char_length(btrim(title)) between 1 and 180),
  edition_label text not null check (char_length(btrim(edition_label)) between 1 and 120),
  publication_year smallint check (publication_year is null or publication_year between 2000 and 2100),
  publisher text check (publisher is null or char_length(publisher) <= 160),
  source_name text check (source_name is null or char_length(source_name) <= 160),
  source_url text check (source_url is null or (source_url ~ '^https://' and char_length(source_url) <= 2048)),
  storage_path text not null unique check (
    storage_path like 'textbooks/%' and
    storage_path !~ '(^|/)\.\.(/|$)' and
    char_length(storage_path) <= 512
  ),
  mime_type text not null default 'application/pdf' check (mime_type = 'application/pdf'),
  file_size_bytes bigint not null check (file_size_bytes between 1 and 20971520),
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  uploaded_by uuid not null references public.profiles(id) on delete restrict,
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint textbook_catalog_review_state_check check (
    (status = 'pending' and reviewed_by is null and reviewed_at is null)
    or (status in ('approved','rejected') and reviewed_by is not null and reviewed_at is not null)
  )
);
create index if not exists textbook_catalog_grade_subject_status_idx
  on public.textbook_catalog (grade_name, subject_name, status, updated_at desc);
create index if not exists textbook_catalog_class_idx
  on public.textbook_catalog (class_name) where class_name is not null;
alter table public.textbook_catalog enable row level security;
drop policy if exists "textbook catalog approved or admin read" on public.textbook_catalog;
create policy "textbook catalog approved or admin read"
  on public.textbook_catalog for select to authenticated
  using (
    private.is_school_admin()
    or (status = 'approved' and (class_name is null or class_name = private.current_school_class()))
  );
drop policy if exists "textbook catalog admin insert" on public.textbook_catalog;
create policy "textbook catalog admin insert"
  on public.textbook_catalog for insert to authenticated
  with check (private.is_school_admin() and uploaded_by = (select auth.uid()));
drop policy if exists "textbook catalog admin update" on public.textbook_catalog;
create policy "textbook catalog admin update"
  on public.textbook_catalog for update to authenticated
  using (private.is_school_admin())
  with check (private.is_school_admin());
drop policy if exists "textbook catalog admin delete" on public.textbook_catalog;
create policy "textbook catalog admin delete"
  on public.textbook_catalog for delete to authenticated
  using (private.is_school_admin());
grant select, insert, update, delete on public.textbook_catalog to authenticated;
drop policy if exists "ruven textbook approved file read" on storage.objects;
create policy "ruven textbook approved file read"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'ruven-school-files'
    and exists (
      select 1 from public.textbook_catalog tc
      where tc.storage_path = storage.objects.name
        and tc.status = 'approved'
        and (tc.class_name is null or tc.class_name = private.current_school_class())
    )
  );
