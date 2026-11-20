create sequence license_serial_seq start with 1;

create table license (
    id                uuid         primary key,
    clinic_id         uuid         not null references clinic (id),
    serial            bigint       not null,
    plan              varchar(50)  not null,
    modules           text[]       not null,
    max_users         int          not null check (max_users > 0),
    max_branches      int          not null check (max_branches > 0),
    starts_on         date         not null,
    ends_on           date         not null,
    valid_from        timestamptz  not null,
    valid_until       timestamptz  not null,
    expiry_grace_days int          not null check (expiry_grace_days between 0 and 90),
    issued_at         timestamptz  not null,
    issued_by         uuid         not null,
    key_id            varchar(50)  not null,
    payload           bytea        not null,
    signature         bytea        not null,
    renewed_from      uuid         references license (id),
    superseded_at     timestamptz,
    superseded_by     uuid         references license (id),
    revoked_at        timestamptz,
    revoked_by        uuid,
    revoke_reason     varchar(500),
    version           int          not null default 0,
    check (ends_on >= starts_on),
    check (valid_until > valid_from)
);

create unique index ux_license_serial on license (serial);
-- At most one current (not superseded, not revoked) license per clinic.
create unique index ux_license_current on license (clinic_id) where revoked_at is null and superseded_at is null;
create index ix_license_clinic on license (clinic_id, issued_at desc);
