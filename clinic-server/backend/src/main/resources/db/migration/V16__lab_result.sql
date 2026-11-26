-- One lab result per paid (or Admin-allowed) service that has a lab form. Created as WAITING when the service is
-- released; the form version is fixed when the lab first saves values; READY makes it visible to doctors and printable.
create table lab_result (
    id                   uuid         primary key,
    clinic_id            uuid         not null,
    branch_id            uuid         not null,
    visit_item_id        uuid         not null unique references visit_item (id),
    visit_id             uuid         not null references visit (id),
    patient_id           uuid         not null references patient (id),
    service_name         varchar(200) not null,
    template_id          uuid         not null references lab_template (id),
    template_version_id  uuid         references lab_template_version (id),
    state                varchar(20)  not null default 'NONE',
    custom_state         varchar(60),
    values               jsonb        not null default '{}',
    comment              text,
    status               varchar(10)  not null check (status in ('WAITING', 'DRAFT', 'READY')),
    entered_by           uuid,
    ready_by             uuid,
    ready_by_name        varchar(200),
    ready_at             timestamptz,
    corrected_at         timestamptz,
    correction_reason    varchar(500),
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              int          not null default 0,
    check ((status = 'READY') = (ready_at is not null))
);

create index ix_lab_result_work on lab_result (created_at desc) where status <> 'READY';
create index ix_lab_result_day on lab_result (created_at desc);
create index ix_lab_result_patient on lab_result (patient_id, created_at desc);
