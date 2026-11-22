-- Allow teachers to publish subject-specific notices (including exam scope notices)
-- only for subject/class pairs explicitly assigned to their authenticated account.
-- Homework and preparation permissions remain scoped to the same assignment check.

DROP POLICY IF EXISTS "school_items_insert_access" ON public.school_items;

CREATE POLICY "school_items_insert_access"
ON public.school_items
FOR INSERT
TO authenticated
WITH CHECK (
  private.is_school_owner()
  OR (
    private.is_school_admin()
    AND created_by = (SELECT auth.uid())
    AND item_type = ANY (ARRAY[
      'timetable'::public.school_item_type,
      'announcement'::public.school_item_type,
      'homework'::public.school_item_type,
      'preparation'::public.school_item_type
    ])
  )
  OR (
    (SELECT private.current_school_role()) = 'class_monitor'::public.school_role
    AND created_by = (SELECT auth.uid())
    AND class_name = (SELECT private.current_school_class())
    AND item_type IN (
      'report'::public.school_item_type,
      'timetable'::public.school_item_type
    )
    AND published = false
  )
  OR (
    (SELECT private.current_school_role()) = 'teacher'::public.school_role
    AND created_by = (SELECT auth.uid())
    AND item_type IN (
      'homework'::public.school_item_type,
      'preparation'::public.school_item_type,
      'announcement'::public.school_item_type
    )
    AND subject_name IS NOT NULL
    AND class_name IS NOT NULL
    AND private.teacher_has_assignment(subject_name, class_name)
  )
);
