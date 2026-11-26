-- Doctors' written conclusions. Free text inside a fixed printed layout (letterhead, patient, signature).
-- The author may edit only on the day it was written (Tashkent date); later corrections are dated notes.
create table conclusion (
    id             uuid         primary key,
    clinic_id      uuid         not null,
    branch_id      uuid         not null,
    patient_id     uuid         not null references patient (id),
    visit_id       uuid         not null references visit (id),
    visit_item_id  uuid         not null unique references visit_item (id),
    author_id      uuid         not null,
    author_name    varchar(200) not null,
    title          varchar(200) not null,
    body           text         not null,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        int          not null default 0
);

create index ix_conclusion_patient on conclusion (patient_id, created_at desc);

create table conclusion_note (
    id             uuid         primary key,
    conclusion_id  uuid         not null references conclusion (id),
    author_id      uuid         not null,
    author_name    varchar(200) not null,
    text           text         not null,
    created_at     timestamptz  not null
);

create index ix_conclusion_note on conclusion_note (conclusion_id, created_at);

-- Each doctor's own reusable texts ("normal ECG", ...).
create table conclusion_template (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    owner_id    uuid         not null,
    name        varchar(100) not null,
    body        text         not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_conclusion_template_name on conclusion_template (owner_id, lower(name)) where deleted_at is null;
