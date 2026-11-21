-- Consolidate overlapping permissive policies without changing their effective
-- USING / WITH CHECK predicates. This preserves the existing role model and
-- does not claim multi-school isolation.

drop policy if exists "owner manages all sections" on public.class_sections;
drop policy if exists "users read open sections" on public.class_sections;
create policy "class_sections_select_access" on public.class_sections for select to authenticated
using (private.is_school_owner() or is_open = true);
create policy "class_sections_insert_owner" on public.class_sections for insert to authenticated
with check (private.is_school_owner());
create policy "class_sections_update_owner" on public.class_sections for update to authenticated
using (private.is_school_owner()) with check (private.is_school_owner());
create policy "class_sections_delete_owner" on public.class_sections for delete to authenticated
using (private.is_school_owner());

drop policy if exists "admins manage reports" on public.daily_class_reports;
drop policy if exists "monitors create own class reports" on public.daily_class_reports;
drop policy if exists "published class reports visible" on public.daily_class_reports;
drop policy if exists "monitors update own reports" on public.daily_class_reports;
create policy "daily_reports_select_access" on public.daily_class_reports for select to authenticated
using (
 private.is_school_admin() or created_by = (select auth.uid())
 or (is_published = true and class_name = (select p.class_name from public.profiles p where p.id = (select auth.uid())))
);
create policy "daily_reports_insert_access" on public.daily_class_reports for insert to authenticated
with check (
 private.is_school_admin() or (
  (select private.current_school_role()::text) = 'class_monitor'
  and created_by = (select auth.uid())
  and class_name = (select p.class_name from public.profiles p where p.id = (select auth.uid()))
 )
);
create policy "daily_reports_update_access" on public.daily_class_reports for update to authenticated
using (private.is_school_admin() or (created_by = (select auth.uid()) and (select private.current_school_role()::text) = 'class_monitor'))
with check (private.is_school_admin() or (created_by = (select auth.uid()) and (select private.current_school_role()::text) = 'class_monitor'));
create policy "daily_reports_delete_admin" on public.daily_class_reports for delete to authenticated
using (private.is_school_admin());

drop policy if exists "admins review leave" on public.leave_requests;
drop policy if exists "monitors record approved leave attendance" on public.leave_requests;
create policy "leave_requests_update_access" on public.leave_requests for update to authenticated
using (
 private.is_school_admin() or (
  (select private.current_school_role()::text) = 'class_monitor'
  and status = 'approved'
  and class_name = (select p.class_name from public.profiles p where p.id = (select auth.uid()))
 )
)
with check (
 (
  private.is_school_admin() and reviewed_by = (select auth.uid())
  and (status <> 'approved' or exists (
   select 1 from public.school_seals s
   where s.id = leave_requests.decision_seal_id
    and s.owner_id = (select auth.uid()) and s.is_active = true
  ))
 )
 or (
  (select private.current_school_role()::text) = 'class_monitor'
  and status = 'approved'
  and class_name = (select p.class_name from public.profiles p where p.id = (select auth.uid()))
  and attendance_marked_by = (select auth.uid())
 )
);

drop policy if exists "admins manage profiles" on public.profiles;
drop policy if exists "owner manages profiles" on public.profiles;
drop policy if exists "profile directory scoped access" on public.profiles;
drop policy if exists "users update own profile name" on public.profiles;
create policy "profiles_select_access" on public.profiles for select to authenticated
using (
 id = (select auth.uid()) or private.is_school_admin() or private.is_school_owner()
 or (
  (select private.current_school_role()::text) = 'class_monitor'
  and (
   ((role)::text = 'student' and class_name = (select private.current_school_class()))
   or (role)::text = any (array['teacher'::text,'class_supervisor'::text,'assistant_principal'::text,'principal'::text,'educational_counselor'::text])
  )
 )
);
create policy "profiles_insert_admin_owner" on public.profiles for insert to authenticated
with check (private.is_school_admin() or private.is_school_owner());
create policy "profiles_update_access" on public.profiles for update to authenticated
using (private.is_school_admin() or private.is_school_owner() or id = (select auth.uid()))
with check (private.is_school_admin() or private.is_school_owner() or id = (select auth.uid()));
create policy "profiles_delete_admin_owner" on public.profiles for delete to authenticated
using (private.is_school_admin() or private.is_school_owner());

drop policy if exists "owner manages report updates" on public.report_updates;
drop policy if exists "admins delete report updates" on public.report_updates;
drop policy if exists "class monitor creates report updates" on public.report_updates;
drop policy if exists "admins read report updates" on public.report_updates;
drop policy if exists "admins update report updates" on public.report_updates;
create policy "report_updates_select_access" on public.report_updates for select to authenticated
using (private.is_school_owner() or private.is_school_admin() or updated_by = (select auth.uid()));
create policy "report_updates_insert_access" on public.report_updates for insert to authenticated
with check (
 private.is_school_owner() or (
  private.current_school_role() = 'class_monitor'::public.school_role
  and updated_by = (select auth.uid())
  and exists (select 1 from public.school_items si where si.id = report_updates.report_id and si.item_type = 'report'::public.school_item_type)
 )
);
create policy "report_updates_update_access" on public.report_updates for update to authenticated
using (private.is_school_owner() or private.is_school_admin())
with check (private.is_school_owner() or private.is_school_admin());
create policy "report_updates_delete_access" on public.report_updates for delete to authenticated
using (private.is_school_owner() or private.is_school_admin());

drop policy if exists "admins update decisions" on public.school_decisions;
drop policy if exists "assigned monitors execute decisions" on public.school_decisions;
create policy "school_decisions_update_access" on public.school_decisions for update to authenticated
using (
 private.is_school_admin() or (
  assigned_monitor = (select auth.uid())
  and (select private.current_school_role()::text) = 'class_monitor'
  and status = any (array['pending'::text,'acknowledged'::text])
 )
)
with check (
 private.is_school_admin() or (
  assigned_monitor = (select auth.uid())
  and (select private.current_school_role()::text) = 'class_monitor'
  and status = any (array['acknowledged'::text,'completed'::text])
 )
);

drop policy if exists "owner manages school items" on public.school_items;
drop policy if exists "admins delete school content" on public.school_items;
drop policy if exists "admins insert school content" on public.school_items;
drop policy if exists "class monitor submits reports and timetable drafts" on public.school_items;
drop policy if exists "teachers create assigned homework and preparations" on public.school_items;
drop policy if exists "read published class content" on public.school_items;
drop policy if exists "admins update school content" on public.school_items;
create policy "school_items_select_access" on public.school_items for select to authenticated
using (
 private.is_school_owner() or private.is_school_admin()
 or (published = true and (class_name is null or class_name = (select p.class_name from public.profiles p where p.id = (select auth.uid()))))
 or created_by = (select auth.uid())
);
create policy "school_items_insert_access" on public.school_items for insert to authenticated
with check (
 private.is_school_owner()
 or (private.is_school_admin() and created_by = (select auth.uid())
  and item_type = any (array['timetable'::public.school_item_type,'announcement'::public.school_item_type,'homework'::public.school_item_type,'preparation'::public.school_item_type]))
 or (private.current_school_role() = 'class_monitor'::public.school_role and created_by = (select auth.uid())
  and (item_type = 'report'::public.school_item_type or (item_type = 'timetable'::public.school_item_type and published = false)))
 or (private.current_school_role() = 'teacher'::public.school_role and created_by = (select auth.uid())
  and item_type = any (array['homework'::public.school_item_type,'preparation'::public.school_item_type])
  and private.teacher_has_assignment(subject_name,class_name))
);
create policy "school_items_update_access" on public.school_items for update to authenticated
using (private.is_school_owner() or private.is_school_admin())
with check (private.is_school_owner() or private.is_school_admin());
create policy "school_items_delete_access" on public.school_items for delete to authenticated
using (private.is_school_owner() or private.is_school_admin());

drop policy if exists "school admins read study records" on public.student_study_records;
drop policy if exists "students read own study records" on public.student_study_records;
create policy "study_records_select_access" on public.student_study_records for select to authenticated
using ((select private.is_school_admin()) or student_id = (select auth.uid()));

drop policy if exists "admins manage teacher assignments" on public.teacher_subjects;
drop policy if exists "owner manages teacher assignments" on public.teacher_subjects;
drop policy if exists "authenticated users read assignments" on public.teacher_subjects;
create policy "teacher_subjects_select_authenticated" on public.teacher_subjects for select to authenticated using (true);
create policy "teacher_subjects_insert_admin_owner" on public.teacher_subjects for insert to authenticated
with check (private.is_school_admin() or private.is_school_owner());
create policy "teacher_subjects_update_admin_owner" on public.teacher_subjects for update to authenticated
using (private.is_school_admin() or private.is_school_owner())
with check (private.is_school_admin() or private.is_school_owner());
create policy "teacher_subjects_delete_admin_owner" on public.teacher_subjects for delete to authenticated
using (private.is_school_admin() or private.is_school_owner());
