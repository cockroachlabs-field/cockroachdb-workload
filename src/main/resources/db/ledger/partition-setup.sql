-- Change regions to match cluster locality flags
ALTER DATABASE workload PRIMARY REGION "eu-central-1";
ALTER DATABASE workload ADD REGION "eu-west-1";
ALTER DATABASE workload ADD REGION "eu-west-2";
ALTER DATABASE workload ADD REGION "us-east-1";
ALTER DATABASE workload ADD REGION "us-east-2";
ALTER DATABASE workload ADD REGION "us-west-1";

ALTER TABLE account ADD COLUMN crdb_region crdb_internal_region AS (region::crdb_internal_region) VIRTUAL NOT VISIBLE NOT NULL;
ALTER TABLE transaction ADD COLUMN crdb_region crdb_internal_region AS (region::crdb_internal_region) VIRTUAL NOT VISIBLE NOT NULL;
ALTER TABLE transaction_item ADD COLUMN crdb_region crdb_internal_region AS (region::crdb_internal_region) VIRTUAL NOT VISIBLE NOT NULL;

ALTER TABLE account SET LOCALITY REGIONAL BY ROW AS crdb_region;
ALTER TABLE transaction SET LOCALITY REGIONAL BY ROW AS crdb_region;
ALTER TABLE transaction_item SET LOCALITY REGIONAL BY ROW AS crdb_region;

-- Placement restrictions
SET enable_multiregion_placement_policy=on;
ALTER DATABASE workload PLACEMENT RESTRICTED;
-- ALTER DATABASE workload PLACEMENT DEFAULT;
