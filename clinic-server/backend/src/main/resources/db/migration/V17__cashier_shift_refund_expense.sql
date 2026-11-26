-- Cashier shifts: payments, cash refunds and cash expenses belong to the cashier's open shift; closing it compares the
-- counted cash with expected = opening + cash payments - refunds paid out - expenses.
create sequence cash_shift_no_seq start with 1;

create table cash_shift (
    id             uuid         primary key,
    clinic_id      uuid         not null,
    branch_id      uuid         not null,
    shift_no       bigint       not null,
    cashier_id     uuid         not null,
    cashier_name   varchar(200) not null,
    opening_cash   bigint       not null check (opening_cash >= 0),
    opened_at      timestamptz  not null,
    status         varchar(6)   not null check (status in ('OPEN', 'CLOSED')),
    closed_at      timestamptz,
    expected_cash  bigint,
    counted_cash   bigint       check (counted_cash >= 0),
    close_note     varchar(500),
    version        int          not null default 0,
    check ((status = 'CLOSED') = (closed_at is not null and counted_cash is not null and expected_cash is not null))
);

create unique index ux_cash_shift_no on cash_shift (branch_id, shift_no);
-- One open shift per cashier.
create unique index ux_cash_shift_open on cash_shift (cashier_id) where status = 'OPEN';
create index ix_cash_shift_opened on cash_shift (opened_at desc);

alter table payment add column shift_id uuid references cash_shift (id);
create index ix_payment_shift on payment (shift_id);

create table cash_expense (
    id                  uuid         primary key,
    clinic_id           uuid         not null,
    branch_id           uuid         not null,
    shift_id            uuid         not null references cash_shift (id),
    recipient_staff_id  uuid,
    recipient_name      varchar(200) not null,
    amount              bigint       not null check (amount > 0),
    comment             varchar(1000) not null,
    created_by          uuid         not null,
    created_at          timestamptz  not null
);

create index ix_cash_expense_shift on cash_expense (shift_id, created_at);

-- A refund is approved by the Admin (whole service, reason) and paid out in cash by a cashier from an open shift.
create table refund (
    id                uuid         primary key,
    clinic_id         uuid         not null,
    branch_id         uuid         not null,
    payment_id        uuid         not null references payment (id),
    visit_id          uuid         not null references visit (id),
    visit_item_id     uuid         not null unique references visit_item (id),
    patient_id        uuid         not null references patient (id),
    service_name      varchar(200) not null,
    amount            bigint       not null check (amount >= 0),
    original_method   varchar(10)  not null,
    reason            varchar(500) not null,
    approved_by       uuid         not null,
    approved_by_name  varchar(200) not null,
    approved_at       timestamptz  not null,
    status            varchar(10)  not null check (status in ('APPROVED', 'PAID_OUT')),
    shift_id          uuid         references cash_shift (id),
    paid_out_by       uuid,
    paid_out_at       timestamptz,
    version           int          not null default 0,
    check ((status = 'PAID_OUT') = (shift_id is not null and paid_out_at is not null))
);

create index ix_refund_status on refund (status, approved_at);
create index ix_refund_shift on refund (shift_id);

-- Visit items: a paid service can now be refunded (Admin). Replace the generated status checks.
alter table visit_item add column refunded_at timestamptz;
do $$
declare r record;
begin
    for r in select conname, pg_get_constraintdef(oid) as def from pg_constraint
             where conrelid = 'visit_item'::regclass and contype = 'c'
    loop
        if r.def like '%payment_id%' or r.def like '%''UNPAID''%' then
            execute format('alter table visit_item drop constraint %I', r.conname);
        end if;
    end loop;
end $$;
alter table visit_item add constraint ck_visit_item_status check (status in ('UNPAID', 'PAID', 'CANCELLED', 'REFUNDED'));
alter table visit_item add constraint ck_visit_item_payment
    check ((status in ('PAID', 'REFUNDED')) = (payment_id is not null and paid_at is not null));
alter table visit_item add constraint ck_visit_item_refund check ((status = 'REFUNDED') = (refunded_at is not null));
