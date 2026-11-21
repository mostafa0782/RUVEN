-- Restrict class-monitor visibility and writes to the monitor's own class.
-- Preserve student/admin access while closing cross-class leave/report paths.

drop policy if exists "leave request visibility" on public.leave_requests;
create policy "leave request visibility"
on public.leave_requests
for select
to authenticated
using (
  student_id = (select auth.uid())
  or private.is_school_admin()
  or (
    (select private.current_school_role()) = 'class_monitor'::public.school_role
    and class_name = (select private.current_school_class())
    and class_name = (
      select p.class_name
      from public.profiles p
      where p.id = leave_requests.student_id
    )
    and status = 'approved'
  )
);

drop policy if exists "school_items_insert_access" on public.school_items;
create policy "school_items_insert_access"
on public.school_items
for insert
to authenticated
with check (
  private.is_school_owner()
  or (
    private.is_school_admin()
    and created_by = (select auth.uid())
    and item_type = any (array[
      'timetable'::public.school_item_type,
      'announcement'::public.school_item_type,
      'homework'::public.school_item_type,
      'preparation'::public.school_item_type
    ])
  )
  or (
    (select private.current_school_role()) = 'class_monitor'::public.school_role
    and created_by = (select auth.uid())
    and class_name = (select private.current_school_class())
    and item_type in (
      'report'::public.school_item_type,
      'timetable'::public.school_item_type
    )
    and published = false
  )
  or (
    (select private.current_school_role()) = 'teacher'::public.school_role
    and created_by = (select auth.uid())
    and item_type = any (array[
      'homework'::public.school_item_type,
      'preparation'::public.school_item_type
    ])
    and private.teacher_has_assignment(subject_name, class_name)
  )
);

drop policy if exists "school_items_select_access" on public.school_items;
create policy "school_items_select_access"
on public.school_items
for select
to authenticated
using (
  private.is_school_owner()
  or private.is_school_admin()
  or (
    published = true
    and (
      class_name is null
      or class_name = (select private.current_school_class())
    )
  )
  or (
    created_by = (select auth.uid())
    and (
      (select private.current_school_role()) <> 'class_monitor'::public.school_role
      or class_name = (select private.current_school_class())
    )
  )
);

drop policy if exists "report_updates_insert_access" on public.report_updates;
create policy "report_updates_insert_access"
on public.report_updates
for insert
to authenticated
with check (
  private.is_school_owner()
  or (
    (select private.current_school_role()) = 'class_monitor'::public.school_role
    and updated_by = (select auth.uid())
    and exists (
      select 1
      from public.school_items si
      where si.id = report_updates.report_id
        and si.item_type = 'report'::public.school_item_type
        and si.class_name = (select private.current_school_class())
        and si.created_by = (select auth.uid())
    )
  )
);

drop policy if exists "report_updates_select_access" on public.report_updates;
create policy "report_updates_select_access"
on public.report_updates
for select
to authenticated
using (
  private.is_school_owner()
  or private.is_school_admin()
  or (
    updated_by = (select auth.uid())
    and exists (
      select 1
      from public.school_items si
      where si.id = report_updates.report_id
        and si.class_name = (select private.current_school_class())
        and si.created_by = (select auth.uid())
    )
  )
);

drop policy if exists "admins issue decisions" on public.school_decisions;
create policy "admins issue decisions"
on public.school_decisions
for insert
to authenticated
with check (
  private.is_school_admin()
  and created_by = (select auth.uid())
  and (
    assigned_monitor is null
    or exists (
      select 1
      from public.profiles p
      where p.id = school_decisions.assigned_monitor
        and p.role = 'class_monitor'::public.school_role
    )
  )
  and exists (
    select 1
    from public.school_seals s
    where s.id = school_decisions.decision_seal_id
      and s.owner_id = (select auth.uid())
      and s.is_active
  )
);

drop policy if exists "decision visibility" on public.school_decisions;
create policy "decision visibility"
on public.school_decisions
for select
to authenticated
using (
  private.is_school_admin()
  or student_id = (select auth.uid())
  or (
    assigned_monitor = (select auth.uid())
    and (select private.current_school_role()) = 'class_monitor'::public.school_role
  )
);
