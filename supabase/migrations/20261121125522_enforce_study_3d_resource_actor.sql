create or replace function private.enforce_study_3d_resource_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    new.uploaded_by := auth.uid();
    new.status := 'pending';
    new.reviewed_by := null;
    new.reviewed_at := null;
    new.created_at := now();
    new.updated_at := now();
    return new;
  end if;

  if new.uploaded_by is distinct from old.uploaded_by then
    raise exception 'study 3D resource uploader is immutable';
  end if;

  if new.status is distinct from old.status then
    if new.status = 'pending' then
      new.reviewed_by := null;
      new.reviewed_at := null;
    else
      new.reviewed_by := auth.uid();
      new.reviewed_at := now();
    end if;
  else
    new.reviewed_by := old.reviewed_by;
    new.reviewed_at := old.reviewed_at;
  end if;

  new.created_at := old.created_at;
  new.updated_at := now();
  return new;
end;
$$;

revoke all on function private.enforce_study_3d_resource_actor() from public, anon, authenticated;

drop trigger if exists study_3d_resources_enforce_actor on public.study_3d_resources;
create trigger study_3d_resources_enforce_actor
before insert or update on public.study_3d_resources
for each row
execute function private.enforce_study_3d_resource_actor();
