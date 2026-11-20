create table heartbeat (
    id          uuid        primary key, -- client-generated heartbeatId (idempotency key)
    device_id   uuid        not null references device (id),
    received_at timestamptz not null,
    sent_at     timestamptz not null,
    payload     jsonb       not null
);

create index ix_heartbeat_device on heartbeat (device_id, received_at desc);
create index ix_heartbeat_received on heartbeat (received_at);

-- Latest technical snapshot per device (one row, upserted on every heartbeat).
create table device_health (
    device_id              uuid        primary key references device (id),
    clinic_id              uuid        not null,
    branch_id              uuid        not null,
    last_heartbeat_at      timestamptz not null,
    app_version            varchar(50) not null,
    uptime_seconds         bigint      not null,
    disks                  jsonb       not null,
    last_backup_at         timestamptz,
    last_backup_status     varchar(16),
    last_backup_success_at timestamptz,
    last_backup_size_bytes bigint,
    error_count            bigint,
    warning_count          bigint,
    clock_skew_seconds     bigint      not null,
    installed_license_id   uuid
);

create table monitoring_alert (
    id          uuid        primary key,
    device_id   uuid        not null references device (id),
    clinic_id   uuid        not null,
    branch_id   uuid        not null,
    type        varchar(32) not null check (type in ('SERVER_OFFLINE', 'DISK_LOW', 'BACKUP_FAILED', 'BACKUP_STALE', 'CLOCK_SKEW')),
    details     jsonb,
    opened_at   timestamptz not null,
    resolved_at timestamptz
);

create unique index ux_alert_open on monitoring_alert (device_id, type) where resolved_at is null;
create index ix_alert_opened on monitoring_alert (opened_at desc);
