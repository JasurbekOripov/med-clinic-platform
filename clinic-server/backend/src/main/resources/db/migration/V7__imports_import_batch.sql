-- A previewed upload waiting for confirmation (short-lived; rows are re-validated at commit).
create table import_batch (
    id           uuid        primary key,
    clinic_id    uuid        not null,
    branch_id    uuid        not null,
    kind         varchar(30) not null,
    rows         jsonb       not null,
    file_name    varchar(255),
    created_by   uuid        not null,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    committed_at timestamptz
);

create index ix_import_batch_created on import_batch (created_at desc);
