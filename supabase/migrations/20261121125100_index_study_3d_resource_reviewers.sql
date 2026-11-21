create index if not exists study_3d_resources_uploaded_by_idx
  on public.study_3d_resources (uploaded_by);

create index if not exists study_3d_resources_reviewed_by_idx
  on public.study_3d_resources (reviewed_by);
