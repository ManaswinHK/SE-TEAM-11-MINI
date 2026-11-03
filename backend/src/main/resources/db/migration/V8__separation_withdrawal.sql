-- Withdrawn resignations (AAS-F-023) stay on record but are not counted as attrition.
ALTER TABLE separation ADD COLUMN withdrawn_at TIMESTAMP;
