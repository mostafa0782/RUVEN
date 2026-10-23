-- Expand RUVEN account categories without granting new roles administrator privileges.
-- Parliament members must be assigned only after the election result is verified.
ALTER TYPE public.school_role ADD VALUE IF NOT EXISTS 'parliament_member';
ALTER TYPE public.school_role ADD VALUE IF NOT EXISTS 'class_supervisor';
ALTER TYPE public.school_role ADD VALUE IF NOT EXISTS 'educational_counselor';
ALTER TYPE public.school_role ADD VALUE IF NOT EXISTS 'ministry_education_member';

COMMENT ON TYPE public.school_role IS
'RUVEN account roles: student, class_monitor, parliament_member, teacher, class_supervisor, assistant_principal, principal, educational_counselor, ministry_education_member, owner. Role assignment must be performed by trusted administration; election of parliament members must be verified before assigning parliament_member.';
