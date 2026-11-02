-- Roles and databases of the local PostgreSQL (docs/09-infrastructure.md §2.2). Runs once on an empty volume.
-- Each service owns <name>_db through the role <name>_app; other roles cannot connect. Tables, the outbox
-- publication and debezium's SELECT grant come from the service's Flyway migrations (toefly-common-outbox).
-- Service role passwords equal the role name in local only.
\set ON_ERROR_STOP on
\getenv debezium_password DEBEZIUM_PASSWORD
\getenv apicurio_password APICURIO_DB_PASSWORD
\getenv keycloak_password KEYCLOAK_DB_PASSWORD

CREATE ROLE debezium LOGIN REPLICATION PASSWORD :'debezium_password';

CREATE ROLE keycloak_app LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak_db OWNER keycloak_app;
REVOKE CONNECT ON DATABASE keycloak_db FROM PUBLIC;

CREATE ROLE apicurio_app LOGIN PASSWORD :'apicurio_password';
CREATE DATABASE apicurio_db OWNER apicurio_app;
REVOKE CONNECT ON DATABASE apicurio_db FROM PUBLIC;

CREATE ROLE profile_app LOGIN PASSWORD 'profile_app';
CREATE DATABASE profile_db OWNER profile_app;
REVOKE CONNECT ON DATABASE profile_db FROM PUBLIC;

CREATE ROLE content_app LOGIN PASSWORD 'content_app';
CREATE DATABASE content_db OWNER content_app;
REVOKE CONNECT ON DATABASE content_db FROM PUBLIC;

CREATE ROLE factory_app LOGIN PASSWORD 'factory_app';
CREATE DATABASE factory_db OWNER factory_app;
REVOKE CONNECT ON DATABASE factory_db FROM PUBLIC;

CREATE ROLE practice_app LOGIN PASSWORD 'practice_app';
CREATE DATABASE practice_db OWNER practice_app;
REVOKE CONNECT ON DATABASE practice_db FROM PUBLIC;

CREATE ROLE assessment_app LOGIN PASSWORD 'assessment_app';
CREATE DATABASE assessment_db OWNER assessment_app;
REVOKE CONNECT ON DATABASE assessment_db FROM PUBLIC;

CREATE ROLE speech_app LOGIN PASSWORD 'speech_app';
CREATE DATABASE speech_db OWNER speech_app;
REVOKE CONNECT ON DATABASE speech_db FROM PUBLIC;

CREATE ROLE planning_app LOGIN PASSWORD 'planning_app';
CREATE DATABASE planning_db OWNER planning_app;
REVOKE CONNECT ON DATABASE planning_db FROM PUBLIC;

CREATE ROLE tutor_agent_app LOGIN PASSWORD 'tutor_agent_app';
CREATE DATABASE tutor_agent_db OWNER tutor_agent_app;
REVOKE CONNECT ON DATABASE tutor_agent_db FROM PUBLIC;

CREATE ROLE mockexam_app LOGIN PASSWORD 'mockexam_app';
CREATE DATABASE mockexam_db OWNER mockexam_app;
REVOKE CONNECT ON DATABASE mockexam_db FROM PUBLIC;

CREATE ROLE vocabulary_app LOGIN PASSWORD 'vocabulary_app';
CREATE DATABASE vocabulary_db OWNER vocabulary_app;
REVOKE CONNECT ON DATABASE vocabulary_db FROM PUBLIC;

CREATE ROLE classroom_app LOGIN PASSWORD 'classroom_app';
CREATE DATABASE classroom_db OWNER classroom_app;
REVOKE CONNECT ON DATABASE classroom_db FROM PUBLIC;

CREATE ROLE billing_app LOGIN PASSWORD 'billing_app';
CREATE DATABASE billing_db OWNER billing_app;
REVOKE CONNECT ON DATABASE billing_db FROM PUBLIC;

CREATE ROLE notification_app LOGIN PASSWORD 'notification_app';
CREATE DATABASE notification_db OWNER notification_app;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;

CREATE ROLE analytics_app LOGIN PASSWORD 'analytics_app';
CREATE DATABASE analytics_db OWNER analytics_app;
REVOKE CONNECT ON DATABASE analytics_db FROM PUBLIC;

CREATE ROLE ai_gateway_app LOGIN PASSWORD 'ai_gateway_app';
CREATE DATABASE ai_gateway_db OWNER ai_gateway_app;
REVOKE CONNECT ON DATABASE ai_gateway_db FROM PUBLIC;

-- Debezium reads the outbox of every service that publishes events (13 of 15).
GRANT CONNECT ON DATABASE profile_db TO debezium;
GRANT CONNECT ON DATABASE content_db TO debezium;
GRANT CONNECT ON DATABASE factory_db TO debezium;
GRANT CONNECT ON DATABASE practice_db TO debezium;
GRANT CONNECT ON DATABASE assessment_db TO debezium;
GRANT CONNECT ON DATABASE speech_db TO debezium;
GRANT CONNECT ON DATABASE planning_db TO debezium;
GRANT CONNECT ON DATABASE tutor_agent_db TO debezium;
GRANT CONNECT ON DATABASE mockexam_db TO debezium;
GRANT CONNECT ON DATABASE vocabulary_db TO debezium;
GRANT CONNECT ON DATABASE classroom_db TO debezium;
GRANT CONNECT ON DATABASE billing_db TO debezium;
GRANT CONNECT ON DATABASE ai_gateway_db TO debezium;

-- Hybrid search (pgvector) and trigram matching.
\connect content_db
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
\connect tutor_agent_db
CREATE EXTENSION IF NOT EXISTS vector;
\connect vocabulary_db
CREATE EXTENSION IF NOT EXISTS pg_trgm;
