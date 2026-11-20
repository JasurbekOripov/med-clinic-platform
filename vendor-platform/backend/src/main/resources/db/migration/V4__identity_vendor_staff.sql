create table vendor_staff (
    id                   uuid         primary key,
    login                varchar(50)  not null,
    full_name            varchar(200) not null,
    role                 varchar(20)  not null check (role in ('OWNER', 'SUPPORT')),
    password_hash        varchar(200) not null,
    active               boolean      not null default true,
    must_change_password boolean      not null default false,
    password_changed_at  timestamptz  not null,
    last_login_at        timestamptz,
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              int          not null default 0
);

-- Logins are stored lower-case; the expression index guards against case variants anyway.
create unique index ux_vendor_staff_login on vendor_staff (lower(login));
