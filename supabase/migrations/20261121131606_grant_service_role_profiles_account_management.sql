-- The owner account Edge Functions use the server-only service_role key.
-- Grant only the operations required to verify the owner and configure a new profile.
grant select, update on table public.profiles to service_role;
