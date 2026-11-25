-- Who this server is (filled once by pairing with the Vendor Platform). Exactly one row.
create table installation (
    id               smallint     primary key default 1 check (id = 1),
    clinic_id        uuid         not null,
    branch_id        uuid         not null,
    clinic_code      varchar(20)  not null,
    branch_code      varchar(24)  not null,
    vendor_url       varchar(300) not null,
    device_id        uuid         not null,
    device_token_enc bytea        not null, -- AES-GCM, key in a local file (Windows DPAPI at the installer milestone)
    heartbeat_seconds int         not null default 60,
    paired_at        timestamptz  not null,
    version          int          not null default 0
);

-- The license file currently installed (verified offline on every check). Exactly one row once installed.
create table installed_license (
    id           smallint    primary key default 1 check (id = 1),
    clinic_id    uuid        not null,
    branch_id    uuid        not null,
    license_id   uuid        not null,
    file         bytea       not null,
    installed_at timestamptz not null,
    revoked_at   timestamptz -- set when the vendor reports the installed license as revoked
);

-- Connection health and the clock guard.
create table vendor_link_state (
    id                  smallint    primary key default 1 check (id = 1),
    last_attempt_at     timestamptz,
    last_success_at     timestamptz,
    last_error_code     varchar(100),
    last_server_time    timestamptz,
    max_seen_time       timestamptz -- highest local time ever observed; going far below it means the clock was moved back
);
insert into vendor_link_state (id) values (1);
