ALTER TABLE commission_rule DROP COLUMN priority;

-- Unused ranges that share an amount with another range cannot stay in the catalog.
DELETE FROM commission_amount_range r
WHERE NOT EXISTS (
    SELECT 1 FROM commission_rule_range rr WHERE rr.range_id = r.id
)
AND EXISTS (
    SELECT 1
    FROM commission_amount_range other
    WHERE other.id <> r.id
      AND NOT (
          (r.max_amount IS NOT NULL AND r.max_amount < other.min_amount)
          OR (other.max_amount IS NOT NULL AND other.max_amount < r.min_amount)
      )
);
