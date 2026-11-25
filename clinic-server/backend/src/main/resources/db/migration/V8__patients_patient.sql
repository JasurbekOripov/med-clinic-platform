create sequence patient_card_seq start with 1;

create table patient (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    card_no     bigint       not null,
    last_name   varchar(100) not null,
    first_name  varchar(100) not null,
    patronymic  varchar(100),
    gender      varchar(6)   not null check (gender in ('MALE', 'FEMALE')),
    birth_date  date         not null,
    phone       varchar(16)  not null,
    address     varchar(500),
    notes       text,
    -- Transliterated, lower-case Latin "last first patronymic" for search and duplicate checks (never displayed).
    search_key  varchar(320) not null,
    created_by  uuid,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

-- Card numbers are unique per branch server (with several branches the display prefixes the branch code).
create unique index ux_patient_card on patient (branch_id, card_no);
create index ix_patient_search_trgm on patient using gin (search_key gin_trgm_ops) where deleted_at is null;
create index ix_patient_phone on patient (phone) where deleted_at is null;
create index ix_patient_birth on patient (birth_date) where deleted_at is null;
create index ix_patient_updated on patient (updated_at desc) where deleted_at is null;
