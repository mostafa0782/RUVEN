-- RUVEN school operations workflows: directories, admin decisions, leave, seals, daily reports, settings, notifications.
create table public.school_seals (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references public.profiles(id) on delete restrict,
 seal_name text not null check(char_length(trim(seal_name)) between 2 and 100),
 seal_title text not null check(char_length(trim(seal_title)) between 2 and 100),
 asset_path text, is_active boolean not null default true, created_at timestamptz not null default now(),
 unique(owner_id,seal_name)
);
create table public.school_decisions (
 id uuid primary key default gen_random_uuid(),
 decision_type text not null check(decision_type in ('transfer_student','remove_student','accept_request','other')),
 student_id uuid not null references public.profiles(id) on delete restrict,
 source_class text, target_class text, reason text not null check(char_length(trim(reason)) between 3 and 3000),
 assigned_monitor uuid references public.profiles(id) on delete set null,
 status text not null default 'pending' check(status in ('pending','acknowledged','completed','cancelled')),
 created_by uuid not null references public.profiles(id) on delete restrict,
 decision_seal_id uuid not null references public.school_seals(id) on delete restrict,
 execution_note text, executed_by uuid references public.profiles(id) on delete restrict, executed_at timestamptz,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.leave_requests (
 id uuid primary key default gen_random_uuid(), student_id uuid not null references public.profiles(id) on delete restrict,
 class_name text not null check(char_length(trim(class_name)) between 1 and 80),
 starts_on date not null, ends_on date not null, reason text not null check(char_length(trim(reason)) between 3 and 2000),
 status text not null default 'pending' check(status in ('pending','approved','rejected','cancelled')),
 reviewed_by uuid references public.profiles(id) on delete restrict, reviewed_at timestamptz,
 decision_seal_id uuid references public.school_seals(id) on delete restrict,
 attendance_status text not null default 'not_recorded' check(attendance_status in ('not_recorded','present','absent','excused')),
 attendance_marked_by uuid references public.profiles(id) on delete restrict, attendance_marked_at timestamptz,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 check(ends_on >= starts_on)
);
create table public.daily_class_reports (
 id uuid primary key default gen_random_uuid(), class_name text not null check(char_length(trim(class_name)) between 1 and 80),
 report_date date not null default current_date, important_events text not null default '' check(char_length(important_events)<=6000),
 promises text not null default '' check(char_length(promises)<=6000), unresolved_items text not null default '' check(char_length(unresolved_items)<=6000),
 created_by uuid not null references public.profiles(id) on delete restrict, is_published boolean not null default false,
 published_at timestamptz, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(class_name,report_date,created_by)
);
create table public.app_user_settings (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 language_mode text not null default 'ar' check(language_mode in ('ar','en','mixed')),
 appearance text not null default 'system' check(appearance in ('system','light','dark','glass')),
 font_family text not null default 'default' check(char_length(font_family)<=80),
 font_scale numeric(3,2) not null default 1.00 check(font_scale between 0.80 and 1.40),
 custom_background_path text, updated_at timestamptz not null default now()
);
create table public.user_notifications (
 id uuid primary key default gen_random_uuid(), recipient_id uuid not null references public.profiles(id) on delete cascade,
 notification_type text not null check(notification_type in ('leave_status','school_decision','general')),
 title text not null check(char_length(title) between 1 and 160), body text not null default '' check(char_length(body)<=2000),
 related_id uuid, read_at timestamptz, created_at timestamptz not null default now()
);
create index school_decisions_monitor_idx on public.school_decisions(assigned_monitor,status,created_at desc);
create index school_decisions_student_idx on public.school_decisions(student_id,created_at desc);
create index leave_requests_student_idx on public.leave_requests(student_id,created_at desc);
create index leave_requests_class_status_idx on public.leave_requests(class_name,status,starts_on);
create index daily_reports_class_date_idx on public.daily_class_reports(class_name,report_date desc,is_published);
create index user_notifications_recipient_idx on public.user_notifications(recipient_id,created_at desc);
alter table public.school_seals enable row level security;
alter table public.school_decisions enable row level security;
alter table public.leave_requests enable row level security;
alter table public.daily_class_reports enable row level security;
alter table public.app_user_settings enable row level security;
alter table public.user_notifications enable row level security;
drop policy if exists "users read own profile or admins read all" on public.profiles;
create policy "profile directory scoped access" on public.profiles for select to authenticated using (
 id=(select auth.uid()) or (select public.is_school_admin()) or
 ((select public.current_school_role()::text)='class_monitor' and
  ((role::text='student' and class_name=(select p.class_name from public.profiles p where p.id=(select auth.uid())))
   or role::text in ('teacher','class_supervisor','assistant_principal','principal','educational_counselor')))
);
create policy "active seals visible" on public.school_seals for select to authenticated using(is_active=true or public.is_school_admin());
create policy "admins manage seals" on public.school_seals for all to authenticated using(public.is_school_admin()) with check(public.is_school_admin());
create policy "decision visibility" on public.school_decisions for select to authenticated using(public.is_school_admin() or student_id=(select auth.uid()) or assigned_monitor=(select auth.uid()));
create policy "admins issue decisions" on public.school_decisions for insert to authenticated with check(
 public.is_school_admin() and created_by=(select auth.uid()) and
 exists(select 1 from public.school_seals s where s.id=decision_seal_id and s.owner_id=(select auth.uid()) and s.is_active));
create policy "admins update decisions" on public.school_decisions for update to authenticated using(public.is_school_admin()) with check(public.is_school_admin());
create policy "assigned monitors execute decisions" on public.school_decisions for update to authenticated
 using(assigned_monitor=(select auth.uid()) and (select public.current_school_role()::text)='class_monitor' and status in ('pending','acknowledged'))
 with check(assigned_monitor=(select auth.uid()) and (select public.current_school_role()::text)='class_monitor' and status in ('acknowledged','completed'));
create policy "leave request visibility" on public.leave_requests for select to authenticated using(
 student_id=(select auth.uid()) or public.is_school_admin() or
 ((select public.current_school_role()::text)='class_monitor' and class_name=(select p.class_name from public.profiles p where p.id=student_id) and status='approved'));
create policy "students submit own leave" on public.leave_requests for insert to authenticated with check(
 student_id=(select auth.uid()) and (select public.current_school_role()::text)='student' and status='pending' and reviewed_by is null and decision_seal_id is null);
create policy "admins review leave" on public.leave_requests for update to authenticated using(public.is_school_admin()) with check(public.is_school_admin());
create policy "monitors record approved leave attendance" on public.leave_requests for update to authenticated
 using((select public.current_school_role()::text)='class_monitor' and status='approved' and class_name=(select p.class_name from public.profiles p where p.id=(select auth.uid())))
 with check((select public.current_school_role()::text)='class_monitor' and status='approved' and class_name=(select p.class_name from public.profiles p where p.id=(select auth.uid())) and attendance_marked_by=(select auth.uid()));
create policy "published class reports visible" on public.daily_class_reports for select to authenticated using(
 public.is_school_admin() or created_by=(select auth.uid()) or
 (is_published=true and class_name=(select p.class_name from public.profiles p where p.id=(select auth.uid()))));
create policy "monitors create own class reports" on public.daily_class_reports for insert to authenticated with check(
 (select public.current_school_role()::text)='class_monitor' and created_by=(select auth.uid()) and
 class_name=(select p.class_name from public.profiles p where p.id=(select auth.uid())));
create policy "monitors update own reports" on public.daily_class_reports for update to authenticated using(
 created_by=(select auth.uid()) and (select public.current_school_role()::text)='class_monitor')
 with check(created_by=(select auth.uid()) and (select public.current_school_role()::text)='class_monitor');
create policy "admins manage reports" on public.daily_class_reports for all to authenticated using(public.is_school_admin()) with check(public.is_school_admin());
create policy "users manage own settings" on public.app_user_settings for all to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));
create policy "users read own notifications" on public.user_notifications for select to authenticated using(recipient_id=(select auth.uid()));
create policy "users mark own notifications read" on public.user_notifications for update to authenticated using(recipient_id=(select auth.uid())) with check(recipient_id=(select auth.uid()));
grant select,insert,update,delete on public.school_seals to authenticated;
grant select,insert on public.school_decisions to authenticated;
grant update(status,execution_note,executed_by,executed_at) on public.school_decisions to authenticated;
grant select,insert on public.leave_requests to authenticated;
grant update(status,reviewed_by,reviewed_at,decision_seal_id,attendance_status,attendance_marked_by,attendance_marked_at) on public.leave_requests to authenticated;
grant select,insert,update on public.daily_class_reports to authenticated;
grant select,insert,update on public.app_user_settings to authenticated;
grant select,update on public.user_notifications to authenticated;
revoke insert,delete on public.user_notifications from authenticated;
revoke delete on public.school_decisions,public.leave_requests from authenticated;
create or replace function public.ruven_notify_leave_status()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
 if new.status is distinct from old.status then
  insert into public.user_notifications(recipient_id,notification_type,title,body,related_id)
  values(new.student_id,'leave_status',
   case when new.status='approved' then 'تمت الموافقة على الإجازة' when new.status='rejected' then 'تم رفض طلب الإجازة' else 'تحديث طلب الإجازة' end,
   case when new.status='approved' then 'تمت مراجعة طلب إجازتك. افتح قسم الإجازات لمعرفة جهة الموافقة والختم.'
        when new.status='rejected' then 'تم رفض طلب إجازتك. افتح قسم الإجازات لمراجعة التفاصيل.'
        else 'تغيّرت حالة طلب الإجازة إلى: '||new.status end,new.id);
 end if;
 return new;
end;
$$;
revoke all on function public.ruven_notify_leave_status() from public,anon,authenticated;
create trigger ruven_leave_status_notification after update of status on public.leave_requests for each row execute function public.ruven_notify_leave_status();
