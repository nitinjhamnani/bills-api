-- The KBill catalog and its call log are no longer used. Bills already stored stay.
DELETE FROM provider_api_log WHERE provider = 'KBILL';
DELETE FROM bbps_location WHERE provider_code = 'KBILL';
DELETE FROM bbps_category WHERE provider_code = 'KBILL';
