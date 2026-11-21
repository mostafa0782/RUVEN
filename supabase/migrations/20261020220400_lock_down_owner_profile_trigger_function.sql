-- Historical hardening step. The same revocation is already present in
-- the owner-profile trigger migration; repeat it here to preserve the
-- remote migration sequence and make the operation idempotent.
REVOKE ALL ON FUNCTION public.handle_new_auth_user() FROM PUBLIC, anon, authenticated;
