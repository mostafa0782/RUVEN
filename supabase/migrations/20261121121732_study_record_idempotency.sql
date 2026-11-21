-- Stable client-generated IDs make retried offline study submissions safe.
alter table public.student_study_records
  add column if not exists client_record_id uuid not null default gen_random_uuid();

create unique index if not exists student_study_records_student_client_record_uidx
  on public.student_study_records (student_id, client_record_id);
