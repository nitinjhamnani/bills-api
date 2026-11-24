DROP INDEX IF EXISTS bills_corp_primary_param_uidx;

CREATE UNIQUE INDEX bills_primary_param_uidx
    ON bills (lower(btrim(primary_param_value)));
