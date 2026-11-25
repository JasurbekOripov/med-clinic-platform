create table role (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    template    varchar(30),           -- ADMIN, RECEPTION, CASHIER, DOCTOR, LAB, NURSE, VENDOR_SUPPORT for seeded roles
    name        varchar(100),          -- custom name; null = translated template name in the UI
    system      boolean      not null, -- ADMIN, VENDOR_SUPPORT: always every permission, cannot be edited or deleted
    permissions text[]       not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0,
    check (template is not null or name is not null)
);

create unique index ux_role_template on role (clinic_id, template) where template is not null and deleted_at is null;
create unique index ux_role_name on role (clinic_id, lower(name)) where name is not null and deleted_at is null;

create table staff (
    id                   uuid         primary key,
    clinic_id            uuid         not null,
    branch_id            uuid         not null,
    role_id              uuid         not null references role (id),
    login                varchar(50)  not null,
    full_name            varchar(200) not null,
    position             varchar(100),
    phone                varchar(16),
    password_hash        varchar(200) not null,
    active               boolean      not null default true,
    must_change_password boolean      not null default false,
    password_changed_at  timestamptz  not null,
    last_login_at        timestamptz,
    granted              text[]       not null default '{}', -- extra permissions on top of the role
    denied               text[]       not null default '{}', -- permissions removed from the role
    signature            bytea,
    signature_type       varchar(20),
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              int          not null default 0
);

create unique index ux_staff_login on staff (lower(login));
create index ix_staff_name_trgm on staff using gin (full_name gin_trgm_ops);
create index ix_staff_role on staff (role_id);
