CREATE TABLE medicine_route_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES item(id),
    version INTEGER NOT NULL CHECK (version > 0),
    routes_csv TEXT NOT NULL,
    concentration TEXT,
    reason TEXT NOT NULL,
    configured_by UUID NOT NULL REFERENCES app_user(id),
    configured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (item_id, version)
);
