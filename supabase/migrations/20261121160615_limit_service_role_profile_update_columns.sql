-- Account provisioning Edge Functions only update these profile attributes.
-- Keep SELECT table-wide for owner verification, but avoid unrestricted UPDATE.
revoke update on table public.profiles from service_role;
grant update (email, full_name, role, class_name, is_owner)
on table public.profiles to service_role;
