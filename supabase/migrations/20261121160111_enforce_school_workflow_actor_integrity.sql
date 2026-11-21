-- Daily reports: the authenticated creator is authoritative, and publish
-- timestamps are server-generated. Monitors cannot move their report across classes.
create or replace function private.enforce_daily_class_report_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authenticated report actor is required';
  end if;

  if tg_op = 'INSERT' then
    new.created_by := auth.uid();
    if (select private.current_school_role()::text) = 'class_monitor'
       and new.class_name is distinct from (select private.current_school_class()) then
      raise exception 'class monitors can only create reports for their own class';
    end if;
    new.created_at := now();
    new.updated_at := now();
    new.published_at := case when new.is_published then now() else null end;
    return new;
  end if;

  if new.id is distinct from old.id
     or new.created_by is distinct from old.created_by
     or new.class_name is distinct from old.class_name
     or new.created_at is distinct from old.created_at then
    raise exception 'daily report identity fields are immutable';
  end if;

  if (select private.current_school_role()::text) = 'class_monitor'
     and (
       old.created_by is distinct from auth.uid()
       or old.class_name is distinct from (select private.current_school_class())
     ) then
    raise exception 'class monitors can only update their own class reports';
  end if;

  if new.is_published is distinct from old.is_published then
    new.published_at := case when new.is_published then now() else null end;
  else
    new.published_at := old.published_at;
  end if;

  new.created_at := old.created_at;
  new.updated_at := now();
  return new;
end;
$$;

revoke all on function private.enforce_daily_class_report_actor()
from public, anon, authenticated;

drop trigger if exists daily_class_reports_enforce_actor on public.daily_class_reports;
create trigger daily_class_reports_enforce_actor
before insert or update on public.daily_class_reports
for each row execute function private.enforce_daily_class_report_actor();

drop policy if exists "daily_reports_update_access" on public.daily_class_reports;
create policy "daily_reports_update_access" on public.daily_class_reports
for update to authenticated
using (
  private.is_school_admin()
  or (
    created_by = (select auth.uid())
    and (select private.current_school_role()::text) = 'class_monitor'
    and class_name = (select private.current_school_class())
  )
)
with check (
  private.is_school_admin()
  or (
    created_by = (select auth.uid())
    and (select private.current_school_role()::text) = 'class_monitor'
    and class_name = (select private.current_school_class())
  )
);

-- Leave requests: only admins may alter review state. Class monitors can update
-- attendance state, while the database stamps the real actor and time.
create or replace function private.enforce_leave_request_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_role text;
  v_admin boolean;
begin
  if auth.uid() is null then
    raise exception 'authenticated leave-request actor is required';
  end if;

  v_role := (select private.current_school_role()::text);
  v_admin := (select private.is_school_admin());

  if tg_op = 'INSERT' then
    if v_role <> 'student' then
      raise exception 'only students can submit leave requests';
    end if;
    new.student_id := auth.uid();
    new.class_name := (select private.current_school_class());
    new.status := 'pending';
    new.reviewed_by := null;
    new.reviewed_at := null;
    new.decision_seal_id := null;
    new.attendance_status := 'not_recorded';
    new.attendance_marked_by := null;
    new.attendance_marked_at := null;
    new.created_at := now();
    new.updated_at := now();
    return new;
  end if;

  if new.id is distinct from old.id
     or new.student_id is distinct from old.student_id
     or new.class_name is distinct from old.class_name
     or new.starts_on is distinct from old.starts_on
     or new.ends_on is distinct from old.ends_on
     or new.reason is distinct from old.reason
     or new.created_at is distinct from old.created_at then
    raise exception 'leave request identity fields are immutable';
  end if;

  if v_admin then
    if new.status is distinct from old.status then
      new.reviewed_by := auth.uid();
      new.reviewed_at := now();
      if new.status <> 'approved' then
        new.decision_seal_id := null;
      end if;
    else
      new.reviewed_by := old.reviewed_by;
      new.reviewed_at := old.reviewed_at;
    end if;

    if new.attendance_status is distinct from old.attendance_status then
      new.attendance_marked_by := auth.uid();
      new.attendance_marked_at := now();
    else
      new.attendance_marked_by := old.attendance_marked_by;
      new.attendance_marked_at := old.attendance_marked_at;
    end if;
  elsif v_role = 'class_monitor' then
    if new.status is distinct from old.status then
      raise exception 'class monitors cannot change leave review status';
    end if;
    new.reviewed_by := old.reviewed_by;
    new.reviewed_at := old.reviewed_at;
    new.decision_seal_id := old.decision_seal_id;

    if new.attendance_status is distinct from old.attendance_status then
      new.attendance_marked_by := auth.uid();
      new.attendance_marked_at := now();
    else
      new.attendance_marked_by := old.attendance_marked_by;
      new.attendance_marked_at := old.attendance_marked_at;
    end if;
  else
    raise exception 'role cannot update leave requests';
  end if;

  new.created_at := old.created_at;
  new.updated_at := now();
  return new;
end;
$$;

revoke all on function private.enforce_leave_request_actor()
from public, anon, authenticated;

drop trigger if exists leave_requests_enforce_actor on public.leave_requests;
create trigger leave_requests_enforce_actor
before insert or update on public.leave_requests
for each row execute function private.enforce_leave_request_actor();

-- School decisions: immutable decision content, with execution identity stamped
-- by the database on the transition to completed.
create or replace function private.enforce_school_decision_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authenticated decision actor is required';
  end if;

  if tg_op = 'INSERT' then
    new.created_by := auth.uid();
    new.status := 'pending';
    new.executed_by := null;
    new.executed_at := null;
    new.created_at := now();
    new.updated_at := now();
    return new;
  end if;

  if new.id is distinct from old.id
     or new.decision_type is distinct from old.decision_type
     or new.student_id is distinct from old.student_id
     or new.source_class is distinct from old.source_class
     or new.target_class is distinct from old.target_class
     or new.reason is distinct from old.reason
     or new.assigned_monitor is distinct from old.assigned_monitor
     or new.created_by is distinct from old.created_by
     or new.decision_seal_id is distinct from old.decision_seal_id
     or new.created_at is distinct from old.created_at then
    raise exception 'school decision identity fields are immutable';
  end if;

  if new.status is distinct from old.status then
    if new.status = 'completed' then
      new.executed_by := auth.uid();
      new.executed_at := now();
    else
      new.executed_by := null;
      new.executed_at := null;
    end if;
  else
    new.executed_by := old.executed_by;
    new.executed_at := old.executed_at;
  end if;

  new.created_at := old.created_at;
  new.updated_at := now();
  return new;
end;
$$;

revoke all on function private.enforce_school_decision_actor()
from public, anon, authenticated;

drop trigger if exists school_decisions_enforce_actor on public.school_decisions;
create trigger school_decisions_enforce_actor
before insert or update on public.school_decisions
for each row execute function private.enforce_school_decision_actor();
