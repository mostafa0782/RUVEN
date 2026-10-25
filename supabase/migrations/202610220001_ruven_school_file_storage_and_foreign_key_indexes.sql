create index if not exists daily_class_reports_created_by_idx on public.daily_class_reports (created_by);
create index if not exists leave_requests_attendance_marked_by_idx on public.leave_requests (attendance_marked_by);
create index if not exists leave_requests_decision_seal_id_idx on public.leave_requests (decision_seal_id);
create index if not exists leave_requests_reviewed_by_idx on public.leave_requests (reviewed_by);
create index if not exists school_decisions_created_by_idx on public.school_decisions (created_by);
create index if not exists school_decisions_decision_seal_id_idx on public.school_decisions (decision_seal_id);
create index if not exists school_decisions_executed_by_idx on public.school_decisions (executed_by);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ruven-school-files', 'ruven-school-files', false, 20971520,
  array['application/pdf','image/jpeg','image/png','image/webp','application/vnd.openxmlformats-officedocument.wordprocessingml.document','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','application/vnd.ms-excel','application/msword'])
on conflict (id) do update set public = false, file_size_limit = 20971520, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "ruven school file upload roles" on storage.objects;
create policy "ruven school file upload roles" on storage.objects for insert to authenticated
with check (bucket_id = 'ruven-school-files' and (select auth.uid()) is not null
  and (public.is_school_admin() or public.current_school_role()::text in ('teacher','class_monitor')));

drop policy if exists "ruven school file read published or owner" on storage.objects;
create policy "ruven school file read published or owner" on storage.objects for select to authenticated
using (bucket_id = 'ruven-school-files' and (
  owner_id = (select auth.uid())::text or public.is_school_admin()
  or exists (select 1 from public.school_items si where si.attachment_path = name and si.published = true
    and (si.class_name is null or si.class_name = public.current_school_class()))
));

drop policy if exists "ruven school file delete owner or admin" on storage.objects;
create policy "ruven school file delete owner or admin" on storage.objects for delete to authenticated
using (bucket_id = 'ruven-school-files' and (owner_id = (select auth.uid())::text or public.is_school_admin()));
