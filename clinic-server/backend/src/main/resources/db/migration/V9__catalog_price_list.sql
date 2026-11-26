-- Price list (Admin only). Money is bigint UZS. Visits will copy name and price at the time of ordering,
-- so later price changes never alter past visits.
create table service_group (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    name        varchar(100) not null,
    sort_order  int          not null default 0,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_service_group_name on service_group (branch_id, lower(name)) where deleted_at is null;

create table service_item (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    group_id    uuid         not null references service_group (id),
    code        varchar(20),
    name        varchar(200) not null,
    price       bigint       not null check (price >= 0),
    -- true for consultations: each doctor may have an own price (service_doctor_price), the base price otherwise
    per_doctor  boolean      not null default false,
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_service_item_code on service_item (branch_id, lower(code)) where code is not null and deleted_at is null;
create unique index ux_service_item_name on service_item (group_id, lower(name)) where deleted_at is null;
create index ix_service_item_group on service_item (group_id) where deleted_at is null;

-- Part of the service aggregate: replaced together with it (the service's version guards concurrent edits).
create table service_doctor_price (
    service_id  uuid    not null references service_item (id),
    staff_id    uuid    not null,
    price       bigint  not null check (price >= 0),
    primary key (service_id, staff_id)
);

create table discount_type (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    name        varchar(100) not null,
    percent     int          not null check (percent between 1 and 100),
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_discount_type_name on discount_type (branch_id, lower(name)) where deleted_at is null;
