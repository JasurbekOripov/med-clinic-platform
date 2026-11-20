create sequence clinic_code_seq start with 1;

create table clinic (
    id             uuid         primary key,
    code           varchar(20)  not null,
    name           varchar(200) not null,
    legal_name     varchar(300),
    owner_name     varchar(200) not null,
    phone          varchar(16)  not null,
    address        varchar(500),
    region         varchar(40)  not null check (region in (
        'KARAKALPAKSTAN', 'ANDIJAN', 'BUKHARA', 'FERGANA', 'JIZZAKH', 'KASHKADARYA', 'KHOREZM', 'NAMANGAN',
        'NAVOI', 'SAMARKAND', 'SURKHANDARYA', 'SYRDARYA', 'TASHKENT_REGION', 'TASHKENT_CITY')),
    notes          text,
    status         varchar(16)  not null check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    next_branch_no int          not null default 1,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    deleted_at     timestamptz,
    version        int          not null default 0
);

-- Codes are never reused, not even after soft delete.
create unique index ux_clinic_code on clinic (code);
create index ix_clinic_name_trgm on clinic using gin (name gin_trgm_ops) where deleted_at is null;
create index ix_clinic_status on clinic (status) where deleted_at is null;

create table branch (
    id         uuid         primary key,
    clinic_id  uuid         not null references clinic (id),
    code       varchar(24)  not null,
    name       varchar(200) not null,
    address    varchar(500),
    phone      varchar(16),
    status     varchar(16)  not null check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    deleted_at timestamptz,
    version    int          not null default 0
);

create unique index ux_branch_code on branch (code);
create unique index ux_branch_clinic_name on branch (clinic_id, lower(name)) where deleted_at is null;
create index ix_branch_clinic on branch (clinic_id) where deleted_at is null;
