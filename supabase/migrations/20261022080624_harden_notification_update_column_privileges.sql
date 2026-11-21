-- Least-privilege hardening: users may only mark their own notifications as read.
-- Row-level security still limits the target row to auth.uid(); column privileges
-- prevent changing notification content, recipient, type, timestamps, or related IDs.
revoke update on table public.user_notifications from authenticated;
grant update (read_at) on table public.user_notifications to authenticated;

drop policy if exists "users mark own notifications read" on public.user_notifications;
create policy "users mark own notifications read"
on public.user_notifications
for update
to authenticated
using (recipient_id = (select auth.uid()))
with check (recipient_id = (select auth.uid()));
