-- Prevent the owner-management RPC from demoting the protected owner account.
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

  IF EXISTS (
    SELECT 1 FROM public.profiles p
    WHERE p.id = target_user_id AND p.is_owner = true
  ) THEN
    RAISE EXCEPTION 'owner_account_is_protected' USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = target_user_id) THEN
    RAISE EXCEPTION 'profile_not_found' USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.profiles
  SET role = new_role,
      class_name = NULLIF(trim(coalesce(new_class_name, '')), ''),
      is_owner = false
  WHERE id = target_user_id AND is_owner = false;
END;
$$;

REVOKE ALL ON FUNCTION public.owner_set_profile_role(uuid, public.school_role, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_set_profile_role(uuid, public.school_role, text) TO authenticated;
