-- Money received at the cashier's desk. A payment is immutable: it copies each paid service line, so the receipt
-- can always be reprinted exactly. Refunds (Admin only) come as separate records in a later milestone.
create sequence receipt_no_seq start with 1;

create table payment (
    id            uuid         primary key,
    clinic_id     uuid         not null,
    branch_id     uuid         not null,
    receipt_no    bigint       not null,
    visit_id      uuid         not null references visit (id),
    visit_no      bigint       not null,
    patient_id    uuid         not null references patient (id),
    amount        bigint       not null check (amount >= 0),
    method        varchar(10)  not null check (method in ('CASH', 'CARD', 'TRANSFER')),
    cashier_id    uuid         not null,
    cashier_name  varchar(200) not null,
    created_at    timestamptz  not null
);

create unique index ux_payment_receipt on payment (branch_id, receipt_no);
create index ix_payment_visit on payment (visit_id, created_at);
create index ix_payment_created on payment (created_at desc);

create table payment_line (
    payment_id     uuid         not null references payment (id),
    line_no        int          not null,
    visit_item_id  uuid         not null unique, -- a service is paid at most once
    service_name   varchar(200) not null,
    doctor_name    varchar(200),
    price          bigint       not null check (price >= 0),
    discount       bigint       not null check (discount >= 0),
    amount         bigint       not null check (amount >= 0),
    primary key (payment_id, line_no)
);
