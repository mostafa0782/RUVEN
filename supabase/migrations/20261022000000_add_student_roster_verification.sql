-- Student roster verification: text-first bulk import and controlled class assignment.
create table if not exists public.student_roster (
  id uuid primary key default gen_random_uuid(),
  full_name text not null check (char_length(btrim(full_name)) between 2 and 160),
  normalized_name text not null,
  grade_name text not null check (char_length(btrim(grade_name)) between 1 and 80),
  section_code text not null check (section_code in ('A','B','C','D','E','F')),
  status text not null default 'unmatched' check (status in ('unmatched','pending_review','verified','duplicate')),
  matched_profile_id uuid references public.profiles(id) on delete set null,
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists student_roster_grade_section_idx on public.student_roster (grade_name, section_code);
create index if not exists student_roster_normalized_name_idx on public.student_roster (normalized_name);
create index if not exists student_roster_matched_profile_idx on public.student_roster (matched_profile_id);
alter table public.student_roster enable row level security;
drop policy if exists "Owner manages student roster" on public.student_roster;
create policy "Owner manages student roster" on public.student_roster
for all to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.is_owner = true))
with check (exists (select 1 from public.profiles p where p.id = auth.uid() and p.is_owner = true));
drop policy if exists "Staff can read roster for review" on public.student_roster;
create policy "Staff can read roster for review" on public.student_roster
for select to authenticated
using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('class_monitor','assistant_principal','principal','owner')));
grant select, insert, update, delete on public.student_roster to authenticated;
