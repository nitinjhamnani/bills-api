-- "Company" is renamed to "Organisation" and "Agency" to "Client" throughout the tenant schema.
-- Two structurally-identical client categories exist going forward: CORPORATE and PARTNER.

UPDATE role SET name = 'ORGANISATION_ADMIN' WHERE name = 'COMPANY_ADMIN';
UPDATE role SET name = 'CLIENT_ADMIN' WHERE name = 'AGENCY_ADMIN';
UPDATE role SET name = 'CLIENT_OPERATOR' WHERE name = 'AGENCY_OPERATOR';

ALTER TABLE agency RENAME TO client;
ALTER TABLE client RENAME COLUMN agency_name TO client_name;
ALTER TABLE client RENAME CONSTRAINT agency_pkey TO client_pkey;
ALTER TABLE client ADD COLUMN client_type VARCHAR(20) NOT NULL DEFAULT 'CORPORATE'
    CHECK (client_type IN ('CORPORATE', 'PARTNER'));

ALTER TABLE company_bill_service_config RENAME TO organisation_bill_service_config;
ALTER TABLE organisation_bill_service_config RENAME CONSTRAINT company_bill_service_config_pkey TO organisation_bill_service_config_pkey;
ALTER TABLE organisation_bill_service_config RENAME CONSTRAINT company_bill_service_config_bill_service_code_key TO organisation_bill_service_config_bill_service_code_key;

ALTER TABLE agency_bill_service_config RENAME TO client_bill_service_config;
ALTER TABLE client_bill_service_config RENAME COLUMN agency_id TO client_id;
ALTER TABLE client_bill_service_config RENAME CONSTRAINT agency_bill_service_config_pkey TO client_bill_service_config_pkey;
ALTER TABLE client_bill_service_config RENAME CONSTRAINT agency_bill_service_config_agency_id_fkey TO client_bill_service_config_client_id_fkey;
ALTER TABLE client_bill_service_config RENAME CONSTRAINT agency_bill_service_config_agency_id_bill_service_code_key TO client_bill_service_config_client_id_bill_service_code_key;

ALTER TABLE app_user RENAME COLUMN agency_id TO client_id;
ALTER TABLE app_user RENAME CONSTRAINT app_user_agency_id_fkey TO app_user_client_id_fkey;

ALTER TABLE wallet_account RENAME COLUMN agency_id TO client_id;
ALTER TABLE wallet_account RENAME CONSTRAINT wallet_account_agency_id_fkey TO wallet_account_client_id_fkey;
ALTER TABLE wallet_account RENAME CONSTRAINT wallet_account_agency_id_wallet_type_key TO wallet_account_client_id_wallet_type_key;

ALTER TABLE wallet_topup_request RENAME COLUMN agency_id TO client_id;
ALTER TABLE wallet_topup_request RENAME CONSTRAINT wallet_topup_request_agency_id_fkey TO wallet_topup_request_client_id_fkey;

ALTER TABLE bill RENAME COLUMN agency_id TO client_id;
ALTER TABLE bill RENAME CONSTRAINT bill_agency_id_fkey TO bill_client_id_fkey;
ALTER INDEX idx_bill_agency RENAME TO idx_bill_client;

ALTER TABLE customer_bill_account RENAME COLUMN agency_id TO client_id;
ALTER TABLE customer_bill_account RENAME CONSTRAINT customer_bill_account_agency_id_fkey TO customer_bill_account_client_id_fkey;
ALTER TABLE customer_bill_account RENAME CONSTRAINT customer_bill_account_agency_id_bill_service_code_customer__key TO customer_bill_account_client_id_bill_service_code_customer__key;
