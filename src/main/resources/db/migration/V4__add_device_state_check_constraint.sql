-- Defence in depth: the application already restricts state to the
-- DeviceState enum, but the column is a VARCHAR, so anything writing to
-- this table outside the application (a migration, a manual fix, another
-- service) could otherwise store a value the application cannot read back.
ALTER TABLE devices
    ADD CONSTRAINT chk_devices_state CHECK (state IN ('AVAILABLE', 'IN_USE', 'INACTIVE'));
