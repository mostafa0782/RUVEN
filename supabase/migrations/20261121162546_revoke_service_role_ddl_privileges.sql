-- The current Edge Functions use service_role only to read profiles and
-- update a small, explicitly granted set of profile columns. It does not need
-- table-level DDL/maintenance capabilities on application tables.
REVOKE TRUNCATE, TRIGGER, REFERENCES, MAINTAIN
ON ALL TABLES IN SCHEMA public
FROM service_role;

-- Keep future application tables created by the postgres migration role
-- from inheriting these unnecessary capabilities.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
REVOKE TRUNCATE, TRIGGER, REFERENCES, MAINTAIN
ON TABLES FROM service_role;
