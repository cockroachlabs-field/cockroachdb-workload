-- Test data

insert into account (balance, currency, name, account_type)
select 500.00,
       'USD',
       md5(random()::text),
       'A'
from generate_series(1, 10) as i;

-- set sql_safe_updates=false;
-- ALTER TABLE account DROP COLUMN crdb_region;

select crdb_internal.validate_multi_region_zone_configs();

show create table account;
show create table transaction;
show create table transaction_item;

SELECT * FROM system.replication_constraint_stats WHERE violating_ranges > 0;

SHOW RANGES from index account@primary;

SELECT * FROM [SHOW RANGES FROM TABLE account] WHERE "start_key" NOT LIKE '%Prefix%';
SELECT * FROM crdb_internal.ranges;

EXPLAIN ANALYZE SELECT * FROM account WHERE region='eu-south-1' and id='02fde064-10b1-4568-be1c-9012f97cd448';
EXPLAIN ANALYZE SELECT * FROM account WHERE region='eu-west-1' and id='02fde064-10b1-4568-be1c-9012f97cd448';
EXPLAIN ANALYZE SELECT * FROM account WHERE region='eu-central-1' and id='02fde064-10b1-4568-be1c-9012f97cd448';

SHOW RANGE FROM TABLE account FOR ROW ('eu-south-1','02fde064-10b1-4568-be1c-9012f97cd448');
SHOW RANGE FROM TABLE account FOR ROW ('eu-west-1','02fde064-10b1-4568-be1c-9012f97cd448');
SHOW RANGE FROM TABLE account FOR ROW ('eu-central-1','02fde064-10b1-4568-be1c-9012f97cd448');

EXPLAIN ANALYZE SELECT * FROM account WHERE region='eu-central-1' and id='0002d214-e300-4ff5-98c9-b81bae2b46e4';
EXPLAIN ANALYZE SELECT * FROM account WHERE id='0002d214-e300-4ff5-98c9-b81bae2b46e4';

