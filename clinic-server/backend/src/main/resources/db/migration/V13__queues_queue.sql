-- Rooms/doctors patients wait for. Numbers restart every day (Tashkent date) per queue: A-001, A-002, ...
create table queue (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    name        varchar(100) not null,
    prefix      varchar(2)   not null check (prefix ~ '^[A-Z]{1,2}$'),
    room        varchar(50),
    -- a doctor's own queue: that doctor's consultations (and other services ordered with this doctor) go here
    doctor_id   uuid,
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_queue_prefix on queue (branch_id, prefix) where deleted_at is null;
create unique index ux_queue_name on queue (branch_id, lower(name)) where deleted_at is null;
create unique index ux_queue_doctor on queue (branch_id, doctor_id) where doctor_id is not null and deleted_at is null;

-- Services without a doctor (lab, ultrasound, ...) are routed by this list; a service belongs to at most one queue.
create table queue_service (
    queue_id    uuid not null references queue (id),
    service_id  uuid not null unique,
    primary key (queue_id, service_id)
);

create table queue_counter (
    queue_id     uuid not null references queue (id),
    day          date not null,
    last_number  int  not null,
    primary key (queue_id, day)
);

-- One ticket per queue per payment: all services of one room paid together share a slip.
create table ticket (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    queue_id    uuid         not null references queue (id),
    day         date         not null,
    number      int          not null,
    visit_id    uuid         not null references visit (id),
    patient_id  uuid         not null references patient (id),
    payment_id  uuid         not null,
    status      varchar(10)  not null check (status in ('WAITING', 'DONE')),
    created_at  timestamptz  not null,
    done_at     timestamptz,
    done_by     uuid,
    version     int          not null default 0,
    unique (queue_id, day, number)
);

create index ix_ticket_payment on ticket (payment_id);

create table ticket_item (
    ticket_id      uuid         not null references ticket (id),
    visit_item_id  uuid         not null unique,
    service_name   varchar(200) not null,
    doctor_name    varchar(200),
    amount         bigint       not null,
    primary key (ticket_id, visit_item_id)
);
