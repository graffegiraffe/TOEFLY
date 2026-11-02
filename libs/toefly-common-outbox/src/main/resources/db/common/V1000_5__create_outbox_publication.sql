-- Logical replication publication of the outbox for the service's Debezium connector (docs/09-infrastructure.md §5.4).
CREATE PUBLICATION dbz_outbox FOR TABLE outbox_event;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
        GRANT SELECT ON outbox_event TO debezium;
    END IF;
END
$$;
