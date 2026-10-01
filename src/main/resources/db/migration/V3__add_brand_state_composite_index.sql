-- (brand, state) serves both the brand+state combined filter and
-- brand-only lookups (a composite btree index also satisfies queries on
-- its leading column alone), making the old single-column brand index
-- redundant. idx_devices_state stays, since state is the trailing column
-- here and wouldn't be served by this index on its own.
CREATE INDEX idx_devices_brand_state ON devices (brand, state);
DROP INDEX idx_devices_brand;
