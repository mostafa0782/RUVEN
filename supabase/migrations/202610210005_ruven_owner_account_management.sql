-- Owner-only account management for RUVEN.
-- Emails are stored in profiles for owner identification; RLS limits row access.
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email text;

UPDATE public.profiles AS p
SET email = u.email
FROM auth.users AS u
WHERE u.id = p.id
  AND p.email IS DISTINCT FROM u.email;

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  is_configured_owner boolean := lower(coalesce(new.email, '')) = 'mostafaminecraft472@gmail.com';
BEGIN
  INSERT INTO public.profiles (id, email, full_name, role, class_name, is_owner)
  VALUES (
    new.id,
    new.email,
    CASE
      WHEN is_configured_owner THEN 'mostafapython'
      ELSE coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''), split_part(coalesce(new.email, ''), '@', 1), 'Student')
    END,
    CASE WHEN is_configured_owner THEN 'owner'::public.school_role ELSE 'student'::public.school_role END,
    NULL,
    is_configured_owner
  )
  ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        full_name = CASE WHEN is_configured_owner THEN 'mostafapython' ELSE public.profiles.full_name END,
        role = CASE WHEN is_configured_owner THEN 'owner'::public.school_role ELSE public.profiles.role END,
        class_name = CASE WHEN is_configured_owner THEN NULL ELSE public.profiles.class_name END,
        is_owner = CASE WHEN is_configured_owner THEN true ELSE public.profiles.is_owner END;

  RETURN new;
END;
$$;

REVOKE ALL ON FUNCTION public.handle_new_auth_user() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.owner_set_profile_role(
  target_user_id uuid,
  new_role public.school_role,
  new_class_name text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.profiles p
    WHERE p.id = (SELECT auth.uid()) AND p.is_owner = true
  ) THEN
    RAISE EXCEPTION 'owner_only' USING ERRCODE = '42501';
  END IF;

  IF new_role = 'owner'::public.school_role THEN
    RAISE EXCEPTION 'owner_role_cannot_be_assigned_from_app' USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = target_user_id) THEN
    RAISE EXCEPTION 'profile_not_found' USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.profiles
  SET role = new_role,
      class_name = NULLIF(trim(coalesce(new_class_name, '')), ''),
      is_owner = false
  WHERE id = target_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.owner_set_profile_role(uuid, public.school_role, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_set_profile_role(uuid, public.school_role, text) TO authenticated;

COMMENT ON FUNCTION public.owner_set_profile_role(uuid, public.school_role, text) IS
'Allows only the configured owner to assign non-owner roles and class names. Owner promotion is intentionally blocked in the app.';
