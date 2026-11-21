-- RLS does not protect TRUNCATE. Remove table-level DDL/maintenance
-- privileges from client roles on existing public tables and future tables
-- created by the application migration owner (postgres).
revoke truncate, trigger, references, maintain
on all tables in schema public
from anon, authenticated;

alter default privileges for role postgres in schema public
revoke truncate, trigger, references, maintain
on tables from anon, authenticated;
