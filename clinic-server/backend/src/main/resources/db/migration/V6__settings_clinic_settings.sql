-- Clinic profile and printing texts. Exactly one row per server, created at pairing.
create table clinic_settings (
    id                 smallint     primary key default 1 check (id = 1),
    clinic_id          uuid         not null,
    branch_id          uuid         not null,
    name               varchar(200) not null,
    legal_name         varchar(300),
    address            varchar(500),
    phone              varchar(16),
    letterhead         text,
    receipt_header     text,
    receipt_footer     text,
    working_hours      jsonb        not null,
    idle_lock_minutes  int          not null default 10 check (idle_lock_minutes between 1 and 240),
    logo               bytea,
    logo_type          varchar(20),
    updated_at         timestamptz  not null,
    version            int          not null default 0
);
