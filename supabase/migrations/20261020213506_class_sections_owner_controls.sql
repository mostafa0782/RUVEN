-- RUVEN section availability and owner-only controls.
-- Apply after 202610200001_initial_schema.sql.
-- Sections are created only when needed. Closed sections are hidden from normal users.
create table public.class_sections (
  id uuid primary key default gen_random_uuid(),
  grade_name text not null check (char_length(trim(grade_name)) between 1 and 80),
  section_code text not null check (section_code in ('A','B','C','D','E','F')),
  is_open boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (grade_name, section_code)
);

-- This flag is only changeable by trusted database administration, never by app users.
alter table public.profiles
  add column is_owner boolean not null default false;

create or replace function public.is_school_owner()
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce((select p.is_owner from public.profiles p where p.id = (select auth.uid())), false)
$$;

alter table public.class_sections enable row level security;

-- Students and staff can only discover sections the owner has opened.
create policy "users read open sections"
on public.class_sections for select to authenticated
using (is_open = true);

-- The owner can view closed sections too, and open/close or create sections.
create policy "owner manages all sections"
on public.class_sections for all to authenticated
using (public.is_school_owner())
with check (public.is_school_owner());

grant select on public.class_sections to authenticated;
grant insert, update, delete on public.class_sections to authenticated;

-- No authenticated user can promote themselves to owner.
revoke update on public.profiles from authenticated;
grant update (full_name) on public.profiles to authenticated;

create trigger class_sections_touch_updated_at
before update on public.class_sections
for each row execute function public.touch_updated_at();

comment on table public.class_sections is
'Only owner-created sections are stored. is_open=false keeps a section hidden from ordinary users.';
comment on column public.profiles.is_owner is
'Trusted provisioning flag. Must only be assigned through SQL Editor or a trusted server-side admin process.';
