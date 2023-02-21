create table if not exists outbox_${partition}
(
    id             uuid        not null default gen_random_uuid(),
    create_time    timestamptz not null default clock_timestamp(),
    aggregate_type string      not null,
    aggregate_id   string      not null,
    event_type     string      not null,
    payload        jsonb       null,

    primary key (id)
);

-- https://www.cockroachlabs.com/docs/v22.1/row-level-ttl.html

alter table outbox_${partition} set
    (ttl_expire_after = '5 minutes', ttl_job_cron = '*/5 * * * *', ttl_select_batch_size = 256);

-- SELECT * FROM outbox_1 WHERE crdb_internal_expiration > now() limit 10;
-- WITH x AS (SHOW JOBS) SELECT * from x WHERE job_type = 'ROW LEVEL TTL';
-- ALTER TABLE events RESET (ttl);