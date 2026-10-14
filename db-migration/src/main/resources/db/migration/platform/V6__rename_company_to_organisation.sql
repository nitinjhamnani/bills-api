-- "Company" is renamed to "Organisation" and "Agency" to "Client" throughout the platform.
ALTER TABLE platform.company RENAME TO organisation;
ALTER TABLE platform.organisation RENAME COLUMN company_code TO organisation_code;
ALTER TABLE platform.organisation RENAME COLUMN company_name TO organisation_name;

ALTER TABLE platform.user_directory RENAME COLUMN agency_id TO client_id;

UPDATE platform.user_directory SET role = 'ORGANISATION_ADMIN' WHERE role = 'COMPANY_ADMIN';
UPDATE platform.user_directory SET role = 'CLIENT_ADMIN' WHERE role = 'AGENCY_ADMIN';
UPDATE platform.user_directory SET role = 'CLIENT_OPERATOR' WHERE role = 'AGENCY_OPERATOR';
