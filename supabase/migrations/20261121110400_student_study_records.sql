create table if not exists public.student_study_records (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  record_type text not null check (record_type in ('study_session','lesson_progress','homework_status','exam_scope')),
  subject_name text not null check (char_length(btrim(subject_name)) between 1 and 80),
  chapter_name text check (chapter_name is null or char_length(chapter_name) <= 160),
  lesson_name text check (lesson_name is null or char_length(lesson_name) <= 200),
  book_title text check (book_title is null or char_length(book_title) <= 200),
  duration_seconds integer not null default 0 check (duration_seconds between 0 and 86400),
  lesson_completed boolean not null default false,
  homework_title text check (homework_title is null or char_length(homework_title) <= 200),
  homework_status text check (homework_status is null or homework_status in ('كُتب','اكتمل','شبه منجز','لم ينجز')),
  exam_kind text check (exam_kind is null or exam_kind in ('شهري','نصف السنة','نهائي','تجريبي','أخرى')),
  page_number integer check (page_number is null or page_number >= 1),
  metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object'),
  recorded_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create index if not exists student_study_records_student_time_idx on public.student_study_records(student_id, recorded_at desc);
create index if not exists student_study_records_type_subject_idx on public.student_study_records(record_type, subject_name, recorded_at desc);
alter table public.student_study_records enable row level security;
create policy "students read own study records" on public.student_study_records for select to authenticated using (student_id = (select auth.uid()));
create policy "students add own study records" on public.student_study_records for insert to authenticated with check (student_id = (select auth.uid()) and (select private.current_school_role()::text) = 'student');
create policy "school admins read study records" on public.student_study_records for select to authenticated using ((select private.is_school_admin()));
grant select, insert on public.student_study_records to authenticated;
revoke update, delete, truncate, references, trigger on public.student_study_records from anon, authenticated;
