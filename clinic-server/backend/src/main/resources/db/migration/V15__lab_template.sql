-- Lab result forms ("shablon"), created by the lab. Every change of the rows is a new version, and results keep the
-- version they were entered with, so editing a form never changes a printed result.
create table lab_template (
    id               uuid         primary key,
    clinic_id        uuid         not null,
    branch_id        uuid         not null,
    name             varchar(200) not null,
    current_version  int          not null,
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    deleted_at       timestamptz,
    version          int          not null default 0
);

create unique index ux_lab_template_name on lab_template (branch_id, lower(name)) where deleted_at is null;

create table lab_template_version (
    id           uuid         primary key,
    template_id  uuid         not null references lab_template (id),
    version_no   int          not null,
    rows         jsonb        not null,
    created_by   uuid,
    created_at   timestamptz  not null,
    unique (template_id, version_no)
);

-- Which price-list services are filled in with this form; a service has at most one form.
create table lab_template_service (
    template_id  uuid not null references lab_template (id),
    service_id   uuid not null unique,
    primary key (template_id, service_id)
);
