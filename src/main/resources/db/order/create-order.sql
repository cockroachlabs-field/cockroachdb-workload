create table if not exists orders
(
    id                  UUID           not null default gen_random_uuid(),
    order_number        integer        not null,
    bill_address1       varchar(255)   null,
    bill_address2       varchar(255)   null,
    bill_city           varchar(255)   null,
    bill_country_code   varchar(3)     null,
    bill_country_name   varchar(128)   null,
    bill_postcode       varchar(16)    null,
    bill_to_first_name  varchar(255)   null,
    bill_to_last_name   varchar(255)   null,
    deliv_to_first_name varchar(255)   null,
    deliv_to_last_name  varchar(255)   null,
    deliv_address1      varchar(255)   null,
    deliv_address2      varchar(255)   null,
    deliv_city          varchar(255)   null,
    deliv_country_code  varchar(3)     null,
    deliv_country_name  varchar(128)   null,
    deliv_postcode      varchar(16)    null,
    status              varchar(64)    null,
    amount              numeric(19, 2) null,
    currency            varchar(3)     null,
    customer_id         UUID           null,
    payment_method_id   UUID           null,
    date_updated        date           not null default current_date(),
    date_placed         date           not null default current_date(),
    customer_profile    jsonb          null,

    primary key (id)
);

CREATE INDEX ON orders (id);