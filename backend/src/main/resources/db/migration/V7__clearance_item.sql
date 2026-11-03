-- Exit clearance checklist (AAS-F-022): a separation is closed only when every item is done.
CREATE TABLE clearance_item (
    id             BIGSERIAL PRIMARY KEY,
    org_id         BIGINT        NOT NULL REFERENCES organization (id),
    separation_id  BIGINT        NOT NULL REFERENCES separation (id),
    name           VARCHAR(100)  NOT NULL,
    sort_order     INTEGER       NOT NULL,
    done           BOOLEAN       NOT NULL DEFAULT FALSE,
    done_by        BIGINT        REFERENCES app_user (id),
    done_at        TIMESTAMP
);

CREATE INDEX idx_clearance_separation ON clearance_item (separation_id);

-- Separations recorded before this change get the standard checklist, not yet ticked.
INSERT INTO clearance_item (org_id, separation_id, name, sort_order)
SELECT s.org_id, s.id, i.name, i.sort_order
FROM separation s
CROSS JOIN (
    SELECT 'Laptop and ID card returned' AS name, 1 AS sort_order
    UNION ALL SELECT 'System access revoked', 2
    UNION ALL SELECT 'Knowledge transfer completed', 3
    UNION ALL SELECT 'Exit interview held', 4
    UNION ALL SELECT 'Final settlement processed', 5
) i;

ALTER TABLE separation ADD COLUMN closed_at TIMESTAMP;
