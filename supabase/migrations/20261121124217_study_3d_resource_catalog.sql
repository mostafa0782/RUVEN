create table if not exists public.study_3d_resources (
  id uuid primary key default gen_random_uuid(),
  subject_name text not null check (length(trim(subject_name)) between 1 and 80),
  chapter_name text,
  title text not null check (length(trim(title)) between 1 and 160),
  description text,
  license_name text not null check (length(trim(license_name)) between 1 and 120),
  attribution text not null check (length(trim(attribution)) between 1 and 240),
  source_url text,
  fallback_2d_description text not null check (length(trim(fallback_2d_description)) between 1 and 1000),
  fallback_2d_url text,
  storage_path text not null unique check (storage_path like 'study-models/%' and position('..' in storage_path) = 0),
  mime_type text not null default 'model/gltf-binary' check (mime_type = 'model/gltf-binary'),
  file_size_bytes bigint not null check (file_size_bytes between 12 and 26214400),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  uploaded_by uuid not null references auth.users(id) on delete restrict,
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint study_3d_source_https check (source_url is null or source_url like 'https://%'),
  constraint study_3d_fallback_https check (fallback_2d_url is null or fallback_2d_url like 'https://%')
);

create index if not exists study_3d_resources_subject_status_idx
  on public.study_3d_resources (subject_name, status, updated_at desc);

alter table public.study_3d_resources enable row level security;

update storage.buckets
set file_size_limit = 26214400,
    allowed_mime_types = array['application/pdf', 'model/gltf-binary']
where id = 'ruven-school-files';

drop policy if exists "ruven study model upload admin guard" on storage.objects;
create policy "ruven study model upload admin guard"
on storage.objects as restrictive
for insert to authenticated
with check (
  bucket_id <> 'ruven-school-files'
  or name not like 'study-models/%'
  or (select private.is_school_admin())
);

drop policy if exists "ruven study model admin upload" on storage.objects;
create policy "ruven study model admin upload"
on storage.objects
for insert to authenticated
with check (
  bucket_id = 'ruven-school-files'
  and name like 'study-models/%'
  and (select private.is_school_admin())
);

drop policy if exists "ruven study model approved read" on storage.objects;
create policy "ruven study model approved read"
on storage.objects
for select to authenticated
using (
  bucket_id = 'ruven-school-files'
  and exists (
    select 1 from public.study_3d_resources r
    where r.storage_path = objects.name
      and r.status = 'approved'
      and (select private.current_school_role())::text = 'student'
  )
);
