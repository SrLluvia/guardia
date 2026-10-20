-- One Postgres instance, one schema per service, one role per service.
-- No role can reach another service's schema, so the service boundary is
-- enforced by the database and not by everyone remembering to behave.
-- In production these would be separate instances; see README.

CREATE ROLE incidents_svc  LOGIN PASSWORD 'incidents_svc';
CREATE ROLE scheduling_svc LOGIN PASSWORD 'scheduling_svc';
CREATE ROLE sla_svc        LOGIN PASSWORD 'sla_svc';

CREATE SCHEMA incidents  AUTHORIZATION incidents_svc;
CREATE SCHEMA scheduling AUTHORIZATION scheduling_svc;
CREATE SCHEMA sla        AUTHORIZATION sla_svc;

-- Nobody gets the public schema, and nobody gets anyone else's.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER ROLE incidents_svc  SET search_path TO incidents;
ALTER ROLE scheduling_svc SET search_path TO scheduling;
ALTER ROLE sla_svc        SET search_path TO sla;
