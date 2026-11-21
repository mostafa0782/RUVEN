-- RUVEN: create a profile for every new Auth user.
-- Only the exact configured owner email receives owner privileges.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  is_configured_owner boolean := lower(coalesce(new.email, '')) = 'mostafaminecraft472@gmail.com';
begin
  insert into public.profiles (id, full_name, role, class_name, is_owner)
  values (
    new.id,
    case
      when is_configured_owner then 'mostafapython'
      else coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''), split_part(coalesce(new.email, ''), '@', 1), 'Student')
    end,
    case when is_configured_owner then 'owner'::public.school_role else 'student'::public.school_role end,
    null,
    is_configured_owner
  )
  on conflict (id) do update
    set full_name = 'mostafapython',
        role = 'owner'::public.school_role,
        class_name = null,
        is_owner = true
    where is_configured_owner;

  return new;
end;
$$;

-- The function is trigger-only; do not expose it as an RPC to clients.
revoke all on function public.handle_new_auth_user() from public;
revoke all on function public.handle_new_auth_user() from anon;
revoke all on function public.handle_new_auth_user() from authenticated;

drop trigger if exists on_auth_user_created_ruven_profile on auth.users;
create trigger on_auth_user_created_ruven_profile
after insert on auth.users
for each row execute function public.handle_new_auth_user();

-- Also promote the account if it already exists when this migration is applied.
insert into public.profiles (id, full_name, role, class_name, is_owner)
select u.id, 'mostafapython', 'owner'::public.school_role, null, true
from auth.users u
where lower(coalesce(u.email, '')) = 'mostafaminecraft472@gmail.com'
on conflict (id) do update
set full_name = excluded.full_name,
    role = 'owner'::public.school_role,
    class_name = null,
    is_owner = true;
