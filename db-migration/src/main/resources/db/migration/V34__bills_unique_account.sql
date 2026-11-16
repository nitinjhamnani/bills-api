-- One corporate cannot manage the same account number twice, on any branch.
CREATE UNIQUE INDEX bills_corp_primary_param_uidx
    ON bills (source_id, lower(btrim(primary_param_value)));
