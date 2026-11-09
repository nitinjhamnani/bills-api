ALTER TABLE client ADD COLUMN corporate_kind varchar(20);

UPDATE client SET corporate_kind = 'COMPANY' WHERE client_type = 'CORPORATE';

ALTER TABLE client ADD CONSTRAINT client_corporate_kind_chk CHECK (
    (client_type = 'CORPORATE' AND corporate_kind IN ('COMPANY', 'INDIVIDUAL'))
    OR (client_type = 'VENDOR' AND corporate_kind IS NULL)
);
