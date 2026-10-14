CREATE TABLE platform.biller_service_catalog (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_code    varchar(30) NOT NULL,
    service_name    varchar(100) NOT NULL,
    category        varchar(50) NOT NULL,
    active          boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT biller_service_catalog_service_code_key UNIQUE (service_code)
);

INSERT INTO platform.biller_service_catalog (service_code, service_name, category) VALUES
    ('ELECTRICITY', 'Electricity Bill', 'UTILITY'),
    ('WATER', 'Water Bill', 'UTILITY'),
    ('GAS', 'Piped Gas Bill', 'UTILITY'),
    ('DTH', 'DTH / Cable TV', 'ENTERTAINMENT'),
    ('BROADBAND', 'Broadband / Fibernet', 'TELECOM'),
    ('MOBILE_POSTPAID', 'Mobile Postpaid', 'TELECOM'),
    ('LANDLINE', 'Landline', 'TELECOM'),
    ('INSURANCE_PREMIUM', 'Insurance Premium', 'INSURANCE')
ON CONFLICT (service_code) DO NOTHING;
