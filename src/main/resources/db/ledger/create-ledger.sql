drop type if exists account_type;

create type if not exists account_type as enum ('A', 'L', 'E', 'R', 'C');

-- drop table if exists account;

create table if not exists account
(
    id             uuid           not null default gen_random_uuid(),
    balance        decimal(19, 2) not null,
    currency       string(3)      not null,
    name           string(128)    not null,
    description    string(256)    null,
    account_type   account_type   not null,
    closed         boolean        not null default false,
    allow_negative integer        not null default 0,
    inserted_at    timestamptz    not null default clock_timestamp(),
    updated_at     timestamptz    null,
    region         string         not null default crdb_internal.locality_value('region'),

    primary key (id)
);
--     region         crdb_internal_region not null default default_to_database_primary_region(gateway_region())::crdb_internal_region,

create table if not exists transaction
(
    id               uuid      not null default gen_random_uuid(),
    booking_date     date      not null default current_date(),
    transfer_date    date      not null default current_date(),
    transaction_type string(3) not null,
    region           string    not null default crdb_internal.locality_value('region'),

    primary key (id)
);

create table if not exists transaction_item
(
    transaction_id  uuid           not null,
    account_id      uuid           not null,
    amount          decimal(19, 2) not null,
    currency        string(3)      not null,
    note            string,
    running_balance decimal(19, 2) not null,
    region          string         not null default crdb_internal.locality_value('region'),

    primary key (transaction_id, account_id)
);

------------------------------------------------
-- Constraints on account
------------------------------------------------

alter table if exists account
    add constraint if not exists check_account_allow_negative check (allow_negative between 0 and 1);
alter table if exists account
    add constraint if not exists check_account_positive_balance check (balance * abs(allow_negative - 1) >= 0);

------------------------------------------------
-- Constraints on transaction_item
------------------------------------------------

alter table if exists transaction_item
    add constraint if not exists fk_region_ref_transaction
        foreign key (transaction_id) references transaction (id);

alter table if exists transaction_item
    add constraint if not exists fk_region_ref_account
        foreign key (account_id) references account (id);
