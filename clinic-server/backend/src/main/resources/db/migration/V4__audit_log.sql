create table audit_log (
    id          uuid         primary key,
    clinic_id   uuid,
    branch_id   uuid,
    occurred_at timestamptz  not null,
    actor_type  varchar(16)  not null check (actor_type in ('STAFF', 'DEVICE', 'SYSTEM')),
    actor_id    uuid,
    actor_name  varchar(200) not null,
    action      varchar(100) not null,
    entity_type varchar(50)  not null,
    entity_id   uuid,
    before      jsonb,
    after       jsonb,
    ip          varchar(64),
    user_agent  varchar(300),
    request_id  varchar(64)
);

create index ix_audit_log_occurred on audit_log (occurred_at desc, id desc);
create index ix_audit_log_entity on audit_log (entity_type, entity_id, occurred_at desc);
create index ix_audit_log_actor on audit_log (actor_id, occurred_at desc);

create function audit_log_immutable() returns trigger
    language plpgsql as
$$
begin
    raise exception 'audit_log is append-only (% denied)', tg_op;
end;
$$;

create trigger trg_audit_log_immutable
    before update or delete on audit_log
    for each row execute function audit_log_immutable();
