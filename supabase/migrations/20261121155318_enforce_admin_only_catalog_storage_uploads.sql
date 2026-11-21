-- Storage policies are permissive by default and combine with OR.
-- The previous "admin guard" was permissive, so it did not constrain the
-- general teacher/monitor upload policy. These restrictive checks are ANDed
-- with all permissive INSERT policies and enforce admin-only catalog paths.
drop policy if exists "ruven study model upload admin guard" on storage.objects;

create policy "ruven textbook uploads admin only"
on storage.objects as restrictive
for insert to authenticated
with check (
  bucket_id <> 'ruven-school-files'
  or name not like 'textbooks/%'
  or (select private.is_school_admin())
);

create policy "ruven study model uploads admin only"
on storage.objects as restrictive
for insert to authenticated
with check (
  bucket_id <> 'ruven-school-files'
  or name not like 'study-models/%'
  or (select private.is_school_admin())
);
