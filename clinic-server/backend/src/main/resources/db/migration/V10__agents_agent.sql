-- Referral agents: people or organisations who send patients. The Admin sets a fixed fee (UZS) per paid referred
-- visit; visits copy the fee at registration, so changing it later does not change what is already owed.
create table agent (
    id          uuid         primary key,
    clinic_id   uuid         not null,
    branch_id   uuid         not null,
    name        varchar(200) not null,
    phone       varchar(16),
    fee         bigint       not null check (fee >= 0),
    notes       varchar(1000),
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    deleted_at  timestamptz,
    version     int          not null default 0
);

create unique index ux_agent_name on agent (branch_id, lower(name)) where deleted_at is null;
