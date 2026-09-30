CREATE TABLE devices (
    id            UUID PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    brand         VARCHAR(255) NOT NULL,
    state         VARCHAR(20) NOT NULL,
    creation_time TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_devices_brand ON devices (brand);
CREATE INDEX idx_devices_state ON devices (state);
