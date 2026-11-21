-- Historical profile bootstrap migration.
-- Never grant owner privileges based on a public or user-supplied email address.
-- Every new Auth account starts with the least-privileged student role.
-- The initial owner is provisioned manually by a trusted project administrator
-- after verifying the account UUID; see supabase/README.md.

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles (id, email, full_name, role, class_name, is_owner)
  VALUES (
    new.id,
    new.email,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'full_name', ''),
      nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
      'Student'
    ),
    'student'::public.school_role,
    NULL,
    false
  )
  ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email;

  RETURN new;
END;
$$;

-- Trigger-only function; never expose it as an RPC.
REVOKE ALL ON FUNCTION public.handle_new_auth_user() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS on_auth_user_created_ruven_profile ON auth.users;
CREATE TRIGGER on_auth_user_created_ruven_profile
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_auth_user();
