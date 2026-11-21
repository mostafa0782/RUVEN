create or replace function public.current_school_class()
returns text language sql stable security definer set search_path=''
as $$ select p.class_name from public.profiles p where p.id=(select auth.uid()) $$;
revoke all on function public.current_school_class() from public,anon;
grant execute on function public.current_school_class() to authenticated;

drop policy if exists "profile directory scoped access" on public.profiles;
create policy "profile directory scoped access" on public.profiles for select to authenticated using (
 id=(select auth.uid()) or (select public.is_school_admin()) or
 ((select public.current_school_role()::text)='class_monitor' and
  ((role::text='student' and class_name=(select public.current_school_class()))
   or role::text in ('teacher','class_supervisor','assistant_principal','principal','educational_counselor')))
);

create or replace function public.ruven_notify_assigned_decision()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
 if new.assigned_monitor is not null then
  insert into public.user_notifications(recipient_id,notification_type,title,body,related_id)
  values(new.assigned_monitor,'school_decision','قرار إداري جديد بانتظار التنفيذ',
    'وصل توجيه من الإدارة. افتح قسم القرارات للاطلاع على التفاصيل وتنفيذ المطلوب.',new.id);
 end if;
 return new;
end;
$$;
revoke all on function public.ruven_notify_assigned_decision() from public,anon,authenticated;
create trigger ruven_decision_assignment_notification after insert on public.school_decisions
for each row execute function public.ruven_notify_assigned_decision();
