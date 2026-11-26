-- A visit groups the services ordered for one patient at the reception desk. Names, prices, the discount and the
-- agent fee are copied at ordering time: later price list changes never alter a visit.
create sequence visit_no_seq start with 1;

create table visit (
    id                uuid         primary key,
    clinic_id         uuid         not null,
    branch_id         uuid         not null,
    visit_no          bigint       not null,
    patient_id        uuid         not null references patient (id),
    discount_type_id  uuid,
    discount_name     varchar(100),
    discount_percent  int          not null default 0 check (discount_percent between 0 and 100),
    agent_id          uuid,
    agent_name        varchar(200),
    agent_fee         bigint       check (agent_fee >= 0),
    created_by        uuid,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           int          not null default 0
);

create unique index ux_visit_no on visit (branch_id, visit_no);
create index ix_visit_patient on visit (patient_id, created_at desc);

create table visit_item (
    id            uuid         primary key,
    visit_id      uuid         not null references visit (id),
    service_id    uuid         not null,
    service_name  varchar(200) not null,
    group_name    varchar(100) not null,
    doctor_id     uuid,
    doctor_name   varchar(200),
    price         bigint       not null check (price >= 0),
    discount      bigint       not null check (discount >= 0 and discount <= price),
    status        varchar(10)  not null check (status in ('UNPAID', 'PAID', 'CANCELLED')),
    payment_id    uuid,
    paid_at       timestamptz,
    -- Admin's emergency permission to serve before payment (recorded as a debt)
    allowed_by    uuid,
    allowed_at    timestamptz,
    allow_reason  varchar(500),
    cancelled_by  uuid,
    cancelled_at  timestamptz,
    cancel_reason varchar(500),
    created_by    uuid,
    created_at    timestamptz  not null,
    check ((status = 'PAID') = (payment_id is not null and paid_at is not null)),
    check ((status = 'CANCELLED') = (cancelled_at is not null))
);

create index ix_visit_item_visit on visit_item (visit_id, created_at);
create index ix_visit_item_unpaid on visit_item (visit_id) where status = 'UNPAID';
