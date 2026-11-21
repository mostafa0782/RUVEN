create or replace function private.enforce_textbook_catalog_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'authenticated reviewer identity is required';
  end if;

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
    raise exception 'textbook uploader is immutable';
  end if;

  if new.storage_path is distinct from old.storage_path then
    raise exception 'textbook storage path is immutable';
  end if;

  if new.mime_type is distinct from old.mime_type
     or new.file_size_bytes is distinct from old.file_size_bytes then
    raise exception 'textbook file metadata is immutable';
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

revoke all on function private.enforce_textbook_catalog_actor()
from public, anon, authenticated;

drop trigger if exists textbook_catalog_enforce_actor on public.textbook_catalog;
create trigger textbook_catalog_enforce_actor
before insert or update on public.textbook_catalog
for each row
execute function private.enforce_textbook_catalog_actor();
