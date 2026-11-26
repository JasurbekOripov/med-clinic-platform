-- History of database backups (nightly and manual). The dump files live on the server's disk (second SSD).
create table backup_run (
    id            uuid          primary key,
    started_at    timestamptz   not null,
    finished_at   timestamptz,
    status        varchar(10)   not null check (status in ('RUNNING', 'SUCCESS', 'FAILED')),
    trigger_kind  varchar(10)   not null check (trigger_kind in ('SCHEDULED', 'MANUAL')),
    triggered_by  uuid,
    file_name     varchar(200),
    size_bytes    bigint,
    sha256        varchar(64),
    error         varchar(1000)
);

create index ix_backup_run_started on backup_run (started_at desc);

create table backup_settings (
    id              int  primary key check (id = 1),
    retention_days  int  not null check (retention_days between 3 and 365),
    version         int  not null default 0
);

insert into backup_settings (id, retention_days) values (1, 30);
