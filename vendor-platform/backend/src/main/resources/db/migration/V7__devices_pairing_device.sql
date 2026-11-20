create table device (
    id            uuid         primary key,
    clinic_id     uuid         not null references clinic (id),
    branch_id     uuid         not null references branch (id),
    token_hash    char(64)     not null,
    status        varchar(16)  not null check (status in ('ACTIVE', 'REVOKED')),
    hostname      varchar(255) not null,
    os            varchar(100) not null,
    app_version   varchar(50)  not null,
    paired_at     timestamptz  not null,
    revoked_at    timestamptz,
    revoke_reason varchar(200),
    last_seen_at  timestamptz,
    last_ip       varchar(64),
    version       int          not null default 0
);

create unique index ux_device_token_hash on device (token_hash);
-- One active Clinic Server per branch; re-pairing revokes the previous one in the same transaction.
create unique index ux_device_active_branch on device (branch_id) where status = 'ACTIVE';
create index ix_device_clinic on device (clinic_id);

create table pairing_code (
    id                uuid        primary key,
    clinic_id         uuid        not null references clinic (id),
    branch_id         uuid        not null references branch (id),
    code_hash         char(64)    not null,
    expires_at        timestamptz not null,
    created_by        uuid        not null,
    created_at        timestamptz not null,
    claimed_at        timestamptz,
    claimed_device_id uuid references device (id),
    cancelled_at      timestamptz
);

create unique index ux_pairing_code_hash on pairing_code (code_hash);
create index ix_pairing_code_branch on pairing_code (branch_id, created_at desc);
