-- RUVEN initial PostgreSQL schema for Supabase.
-- Apply with: supabase db push
-- Access is denied by default; policies below grant only scoped access.

create extension if not exists pgcrypto;

create type public.school_role as enum (
  'student', 'class_monitor', 'teacher', 'assistant_principal', 'principal'
);

create type public.school_item_type as enum (
  'homework', 'preparation', 'timetable', 'announcement', 'report'
);

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null check (char_length(trim(full_name)) between 2 and 100),
  role public.school_role not null default 'student',
  class_name text,
  created_at timestamptz not null default now()
);

-- A student's account is linked to a class. A teacher can only publish for
-- subjects explicitly assigned to them by an administrator.
create table public.teacher_subjects (
  id uuid primary key default gen_random_uuid(),
  teacher_id uuid not null references public.profiles(id) on delete cascade,
  subject_name text not null check (char_length(trim(subject_name)) between 1 and 80),
  class_name text not null check (char_length(trim(class_name)) between 1 and 80),
  unique (teacher_id, subject_name, class_name)
);

create table public.school_items (
  id uuid primary key default gen_random_uuid(),
  item_type public.school_item_type not null,
  title text not null check (char_length(trim(title)) between 1 and 160),
  body text not null default '' check (char_length(body) <= 12000),
  subject_name text,
  class_name text,
  attachment_path text,
  created_by uuid not null references public.profiles(id) on delete restrict,
  published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    item_type <> 'homework'
    or (subject_name is not null and class_name is not null)
  ),
  check (
    item_type <> 'preparation'
    or (subject_name is not null and class_name is not null)
  )
);

create table public.report_updates (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.school_items(id) on delete cascade,
  updated_by uuid not null references public.profiles(id) on delete restrict,
  body text not null check (char_length(trim(body)) between 1 and 4000),
  created_at timestamptz not null default now()
);

create index school_items_feed_idx
  on public.school_items (item_type, class_name, published, created_at desc);
create index school_items_creator_idx on public.school_items (created_by);
create index teacher_subjects_lookup_idx
  on public.teacher_subjects (teacher_id, class_name, subject_name);

create or replace function public.current_school_role()
returns public.school_role
language sql stable security definer
set search_path = ''
as $$
  select p.role from public.profiles p where p.id = (select auth.uid())
$$;

create or replace function public.is_school_admin()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select coalesce(public.current_school_role() in ('principal', 'assistant_principal'), false)
$$;

create or replace function public.teacher_has_assignment(subject text, class_value text)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.teacher_subjects ts
    where ts.teacher_id = (select auth.uid())
      and ts.subject_name = subject
      and ts.class_name = class_value
  )
$$;

alter table public.profiles enable row level security;
alter table public.teacher_subjects enable row level security;
alter table public.school_items enable row level security;
alter table public.report_updates enable row level security;

-- Profiles: authenticated users can read school directory; users can update
-- their own display name only. Role changes are restricted to admins.
create policy "authenticated users read profiles"
  on public.profiles for select to authenticated using (true);
create policy "users create own student profile"
  on public.profiles for insert to authenticated
  with check (id = (select auth.uid()) and role = 'student');
create policy "users update own profile name"
  on public.profiles for update to authenticated
  using (id = (select auth.uid()))
  with check (
    id = (select auth.uid())
    and role = (select role from public.profiles p where p.id = (select auth.uid()))
  );
create policy "admins manage profiles"
  on public.profiles for all to authenticated
  using (public.is_school_admin())
  with check (public.is_school_admin());

create policy "authenticated users read assignments"
  on public.teacher_subjects for select to authenticated using (true);
create policy "admins manage teacher assignments"
  on public.teacher_subjects for all to authenticated
  using (public.is_school_admin())
  with check (public.is_school_admin());

-- Read published content for the user's class; admins can read all. Admins
-- manage school-wide content. Teachers write only assigned subjects/classes.
create policy "read published class content"
  on public.school_items for select to authenticated
  using (
    public.is_school_admin()
    or (
      published = true
      and (class_name is null or class_name = (
        select p.class_name from public.profiles p where p.id = (select auth.uid())
      ))
    )
    or created_by = (select auth.uid())
  );

create policy "admins insert school content"
  on public.school_items for insert to authenticated
  with check (
    public.is_school_admin()
    and created_by = (select auth.uid())
    and item_type in ('timetable', 'announcement', 'homework', 'preparation')
  );

create policy "teachers create assigned homework and preparations"
  on public.school_items for insert to authenticated
  with check (
    public.current_school_role() = 'teacher'
    and created_by = (select auth.uid())
    and item_type in ('homework', 'preparation')
    and public.teacher_has_assignment(subject_name, class_name)
  );

create policy "class monitor submits reports and timetable drafts"
  on public.school_items for insert to authenticated
  with check (
    public.current_school_role() = 'class_monitor'
    and created_by = (select auth.uid())
    and (
      item_type = 'report'
      or (item_type = 'timetable' and published = false)
    )
  );

create policy "admins update school content"
  on public.school_items for update to authenticated
  using (public.is_school_admin())
  with check (public.is_school_admin());

create policy "admins delete school content"
  on public.school_items for delete to authenticated
  using (public.is_school_admin());

create policy "admins read report updates"
  on public.report_updates for select to authenticated
  using (public.is_school_admin() or updated_by = (select auth.uid()));

create policy "class monitor creates report updates"
  on public.report_updates for insert to authenticated
  with check (
    public.current_school_role() = 'class_monitor'
    and updated_by = (select auth.uid())
    and exists (
      select 1 from public.school_items si
      where si.id = report_id and si.item_type = 'report'
    )
  );

create policy "admins update report updates"
  on public.report_updates for update to authenticated
  using (public.is_school_admin())
  with check (public.is_school_admin());

create policy "admins delete report updates"
  on public.report_updates for delete to authenticated
  using (public.is_school_admin());

-- Keep updated_at correct for every update.
create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end
$$;

create trigger school_items_touch_updated_at
before update on public.school_items
for each row execute function public.touch_updated_at();

-- Do not allow ordinary users to grant themselves roles by updating profiles.
revoke update on public.profiles from authenticated;
grant update (full_name, class_name) on public.profiles to authenticated;
grant select, insert on public.profiles to authenticated;
grant select on public.teacher_subjects to authenticated;
grant select, insert, update, delete on public.teacher_subjects to authenticated;
grant select, insert, update, delete on public.school_items to authenticated;
grant select, insert, update, delete on public.report_updates to authenticated;
