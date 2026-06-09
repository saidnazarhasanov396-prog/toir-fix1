-- Run this as the database owner or a DBA role before starting the backend.
-- Replace role names if the environment uses different runtime or migration users.

-- Local/dev quick fix: allow the application user to run Flyway DDL in public.
GRANT USAGE ON SCHEMA public TO "toir-user";
GRANT CREATE ON SCHEMA public TO "toir-user";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL TABLES IN SCHEMA public TO "toir-user";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO "toir-user";

-- Production-safe option: run Flyway with a dedicated migration user.
-- Configure the backend with TOIR_FLYWAY_USER/TOIR_FLYWAY_PASSWORD or
-- SPRING_FLYWAY_USER/SPRING_FLYWAY_PASSWORD after granting the privileges below.
GRANT USAGE ON SCHEMA public TO "toir-flyway";
GRANT CREATE ON SCHEMA public TO "toir-flyway";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL TABLES IN SCHEMA public TO "toir-flyway";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO "toir-flyway";

-- Let the limited runtime user access objects created by the migration user.
ALTER DEFAULT PRIVILEGES FOR ROLE "toir-flyway" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLES TO "toir-user";
ALTER DEFAULT PRIVILEGES FOR ROLE "toir-flyway" IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO "toir-user";
